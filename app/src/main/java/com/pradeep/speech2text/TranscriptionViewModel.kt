package com.pradeep.speech2text

import android.Manifest
import android.app.Application
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.MediaRecorder
import android.net.Uri
import android.os.Build
import android.os.SystemClock
import androidx.annotation.RequiresPermission
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import androidx.core.net.toUri
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.AndroidViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.runBlocking
import android.provider.OpenableColumns
import com.pradeep.speech2text.session.SessionRepository
import com.pradeep.speech2text.session.SessionSource
import com.pradeep.speech2text.session.SessionSummary
import com.pradeep.speech2text.session.TranscriptionRun
import com.pradeep.speech2text.session.TranscriptionSession
import java.text.SimpleDateFormat
import java.util.Date
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.concurrent.Executors
import com.pradeep.speech2text.ui.theme.AppFont
import kotlin.math.max


enum class TranscriptionPhase {
    INITIALIZING,
    READY,
    RECORDING,
    TRANSCRIBING,
    SAVING,
    ERROR,
}

enum class AsrEngineChoice(val label: String) {
    MOONSHINE("Moonshine Base"),
    ZIPFORMER("Zipformer / Transducer");

    companion object {
        fun fromStored(value: String?): AsrEngineChoice = entries.firstOrNull { it.name == value } ?: MOONSHINE
    }
}

data class ComparisonResult(
    val audioDurationSeconds: Double,
    val moonshine: BenchmarkMetrics,
    val moonshineText: String,
    val transducer: BenchmarkMetrics,
    val transducerText: String,
    val hotwordsEnabled: Boolean,
)

data class TranscriptionUiState(
    val phase: TranscriptionPhase = TranscriptionPhase.INITIALIZING,
    val status: String = "Loading offline model…",
    val transcript: String = "",
    val originalTranscript: String = "",
    val isEdited: Boolean = false,
    val isEditing: Boolean = false,
    val activeSessionId: String? = null,
    val activeSessionTitle: String? = null,
    val activeSessionRuns: List<TranscriptionRun> = emptyList(),
    val historySummaries: List<SessionSummary> = emptyList(),
    val historySearchQuery: String = "",
    val filteredSummaries: List<SessionSummary> = emptyList(),
    val metrics: BenchmarkMetrics? = null,
    val hasAudio: Boolean = false,
    val elapsedRecordingMs: Long = 0L,
    val waveform: List<Float> = emptyList(),
    val selectedEngine: AsrEngineChoice = AsrEngineChoice.MOONSHINE,
    val hotwordsEnabled: Boolean = true,
    val comparison: ComparisonResult? = null,
    val appFont: AppFont = AppFont.SEGOE_UI,
)

class TranscriptionViewModel(application: Application) : AndroidViewModel(application) {
    // A single worker serializes capture finalization, inference, and SAF I/O;
    // none of these operations can block Compose's main thread.
    private val worker = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "offline-transcription")
    }
    private val preferences = application.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    private val initialFont = try {
        AppFont.valueOf(preferences.getString(PREFERENCE_FONT, AppFont.SEGOE_UI.name) ?: AppFont.SEGOE_UI.name)
    } catch (_: IllegalArgumentException) {
        AppFont.SEGOE_UI
    }

    private val mutableUiState = MutableStateFlow(TranscriptionUiState(appFont = initialFont))
    val uiState = mutableUiState.asStateFlow()

    @Volatile
    private var selectedEngine = AsrEngineChoice.fromStored(preferences.getString(PREFERENCE_ENGINE, null))

    @Volatile
    private var hotwordsEnabled = preferences.getBoolean(PREFERENCE_HOTWORDS, true)

    @Volatile
    private var isRecording = false

    @Volatile
    private var audioRecord: AudioRecord? = null

    @Volatile
    private var retainedAudio: PcmAudio? = null

    @Volatile
    private var retainedSource: String = ""

    private var engine: TranscriptionEngine? = null

    @Volatile
    private var zipformerEngine: TranscriptionEngine? = null

    val sessionRepository = SessionRepository(application.filesDir)

    init {
        worker.execute {
            try {
                val summaries = sessionRepository.getAllSummaries()
                engine = MoonshineTranscriptionEngine(application) { completed, total ->
                    if (total > 1) {
                        mutableUiState.update {
                            it.copy(status = "Transcribing offline… ($completed/$total)")
                        }
                    }
                }
                mutableUiState.update {
                    it.copy(
                        phase = TranscriptionPhase.READY,
                        status = "Ready — English, fully offline",
                        selectedEngine = selectedEngine,
                        hotwordsEnabled = hotwordsEnabled,
                        historySummaries = summaries,
                        filteredSummaries = summaries,
                    )
                }
            } catch (error: Throwable) {
                mutableUiState.update {
                    it.copy(
                        phase = TranscriptionPhase.ERROR,
                        status = "Could not load the offline model: ${error.message ?: error.javaClass.simpleName}",
                    )
                }
            }
        }
    }

    fun startRecording() {
        if (mutableUiState.value.phase != TranscriptionPhase.READY) return

        val application = getApplication<Application>()
        if (
            ContextCompat.checkSelfPermission(application, Manifest.permission.RECORD_AUDIO) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            mutableUiState.update { it.copy(status = "Microphone permission is required to record.") }
            return
        }

        try {
            startRecorderWithPermission()
        } catch (error: Throwable) {
            mutableUiState.update {
                it.copy(
                    phase = TranscriptionPhase.READY,
                    status = "Could not start the microphone: ${error.message ?: error.javaClass.simpleName}",
                )
            }
        }
    }

    fun selectEngine(choice: AsrEngineChoice) {
        if (mutableUiState.value.phase != TranscriptionPhase.READY || selectedEngine == choice) return
        selectedEngine = choice
        preferences.edit { putString(PREFERENCE_ENGINE, choice.name) }
        mutableUiState.update {
            it.copy(
                selectedEngine = choice,
                status = "Ready — ${choice.label}",
                comparison = null,
            )
        }
    }

    fun setHotwordsEnabled(enabled: Boolean) {
        if (mutableUiState.value.phase != TranscriptionPhase.READY || hotwordsEnabled == enabled) return
        hotwordsEnabled = enabled
        preferences.edit { putBoolean(PREFERENCE_HOTWORDS, enabled) }
        worker.execute {
            zipformerEngine?.close()
            zipformerEngine = null
        }
        mutableUiState.update { it.copy(hotwordsEnabled = enabled, comparison = null) }
    }

    fun selectFont(font: AppFont) {
        preferences.edit { putString(PREFERENCE_FONT, font.name) }
        mutableUiState.update { it.copy(appFont = font) }
    }

    fun compareRetainedAudio() {
        if (mutableUiState.value.phase != TranscriptionPhase.READY) return
        val audio = retainedAudio ?: return
        mutableUiState.update {
            it.copy(phase = TranscriptionPhase.TRANSCRIBING, status = "Comparing Moonshine and Zipformer…")
        }
        worker.execute {
            try {
                val moonshineResult = transcribeWithEngine(engineFor(AsrEngineChoice.MOONSHINE), audio)
                val transducerResult = transcribeWithEngine(engineFor(AsrEngineChoice.ZIPFORMER), audio)
                val comparison = ComparisonResult(
                    audioDurationSeconds = audio.durationSeconds,
                    moonshine = benchmarkMetrics(moonshineResult, audio.durationSeconds),
                    moonshineText = moonshineResult.text,
                    transducer = benchmarkMetrics(transducerResult, audio.durationSeconds),
                    transducerText = transducerResult.text,
                    hotwordsEnabled = hotwordsEnabled,
                )
                val savedComparison = try {
                    savedMusicTreeUri()?.let { saveComparisonToMusic(getApplication(), comparison, it) }
                } catch (_: Throwable) {
                    null
                }
                mutableUiState.update {
                    it.copy(
                        phase = TranscriptionPhase.READY,
                        status = if (savedComparison != null) {
                            "Comparison complete — saved $savedComparison"
                        } else {
                            "Comparison complete (select Music before Compare to save JSON)"
                        },
                        comparison = comparison,
                    )
                }
            } catch (error: Throwable) {
                mutableUiState.update {
                    it.copy(
                        phase = TranscriptionPhase.READY,
                        status = "Comparison failed: ${error.message ?: error.javaClass.simpleName}",
                    )
                }
            }
        }
    }

    private val audioManager = application.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
    private var audioFocusRequest: AudioFocusRequest? = null
    private val audioFocusListener = AudioManager.OnAudioFocusChangeListener { focusChange ->
        if (focusChange == AudioManager.AUDIOFOCUS_LOSS ||
            focusChange == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT
        ) {
            if (isRecording) {
                stopRecording()
            }
        }
    }

    @RequiresPermission(Manifest.permission.RECORD_AUDIO)
    private fun startRecorderWithPermission() {
        val minimumBytes = AudioRecord.getMinBufferSize(
            SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
        )
        check(minimumBytes > 0) { "16 kHz mono recording is unavailable" }

        val recorder = AudioRecord.Builder()
            .setAudioSource(MediaRecorder.AudioSource.VOICE_RECOGNITION)
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(SAMPLE_RATE)
                    .setChannelMask(AudioFormat.CHANNEL_IN_MONO)
                    .build(),
            )
            .setBufferSizeInBytes(max(minimumBytes, READ_BUFFER_SAMPLES * Short.SIZE_BYTES))
            .build()

        if (recorder.state != AudioRecord.STATE_INITIALIZED) {
            recorder.release()
            error("AudioRecord did not initialize")
        }

        // Request audio focus before starting capture
        audioManager?.let { am ->
            val focusReq = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE)
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ASSISTANCE_ACCESSIBILITY)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build()
                )
                .setOnAudioFocusChangeListener(audioFocusListener)
                .build()
            val focusResult = am.requestAudioFocus(focusReq)
            if (focusResult == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
                audioFocusRequest = focusReq
            }
        }

        retainedAudio = null
        retainedSource = ""
        audioRecord = recorder
        isRecording = true
        mutableUiState.update {
            it.copy(
                phase = TranscriptionPhase.RECORDING,
                status = "Recording…",
                transcript = "",
                metrics = null,
                hasAudio = false,
                elapsedRecordingMs = 0L,
                waveform = emptyList(),
            )
        }
        worker.execute { captureAndTranscribe(recorder) }
    }

    fun stopRecording() {
        if (!isRecording) return

        isRecording = false
        abandonAudioFocus()
        mutableUiState.update {
            it.copy(
                phase = TranscriptionPhase.TRANSCRIBING,
                status = "Finishing recording…",
                waveform = emptyList(),
            )
        }
        try {
            audioRecord?.stop()
        } catch (_: IllegalStateException) {
            // Capture may not have entered the recording state yet, or already stopped.
        }
    }

    private fun abandonAudioFocus() {
        audioFocusRequest?.let { req ->
            audioManager?.abandonAudioFocusRequest(req)
            audioFocusRequest = null
        }
    }


    fun importWav(uri: Uri) {
        if (mutableUiState.value.phase != TranscriptionPhase.READY) return

        retainedAudio = null
        retainedSource = ""
        mutableUiState.update {
            it.copy(
                phase = TranscriptionPhase.TRANSCRIBING,
                status = "Reading WAV…",
                transcript = "",
                originalTranscript = "",
                isEdited = false,
                isEditing = false,
                activeSessionId = null,
                activeSessionTitle = null,
                activeSessionRuns = emptyList(),
                metrics = null,
                hasAudio = false,
            )
        }

        worker.execute {
            try {
                val application = getApplication<Application>()
                val importedName = queryDisplayName(application, uri) ?: "audio.wav"
                val imported = checkNotNull(application.contentResolver.openInputStream(uri)) {
                    "Could not open the selected WAV"
                }.use(WavCodec::read)
                val normalized = WavCodec.normalizeForMoonshine(imported)
                val source = "imported WAV (${imported.sampleRate} Hz, ${imported.channels} ch)"
                val defaultTitle = "Imported – $importedName"
                transcribeOnWorker(
                    audio = normalized,
                    source = source,
                    completionMessage = null,
                    sessionTitle = defaultTitle,
                    isImported = true,
                )
            } catch (error: Throwable) {
                retainedAudio = null
                retainedSource = ""
                mutableUiState.update {
                    it.copy(
                        phase = TranscriptionPhase.READY,
                        status = "WAV import failed: ${error.message ?: error.javaClass.simpleName}",
                        transcript = "",
                        metrics = null,
                        hasAudio = false,
                    )
                }
            }
        }
    }

    fun startEditingTranscript() {
        if (mutableUiState.value.phase != TranscriptionPhase.READY) return
        mutableUiState.update { it.copy(isEditing = true) }
    }

    fun cancelEditingTranscript() {
        mutableUiState.update { it.copy(isEditing = false) }
    }

    fun saveTranscriptEdit(newText: String) {
        val currentSessionId = mutableUiState.value.activeSessionId
        worker.execute {
            val updated = if (currentSessionId != null) {
                sessionRepository.updateTranscript(currentSessionId, newText)
            } else null

            val allSummaries = sessionRepository.getAllSummaries()
            mutableUiState.update {
                val orig = it.originalTranscript
                val isEdited = updated?.isEdited ?: (newText != orig)
                val wordCount = countWords(newText)
                it.copy(
                    transcript = newText,
                    isEdited = isEdited,
                    isEditing = false,
                    metrics = it.metrics?.copy(wordCount = wordCount),
                    historySummaries = allSummaries,
                    filteredSummaries = sessionRepository.search(it.historySearchQuery),
                )
            }
        }
    }

    fun restoreOriginalTranscript() {
        val currentSessionId = mutableUiState.value.activeSessionId
        worker.execute {
            val updated = if (currentSessionId != null) {
                sessionRepository.restoreOriginalTranscript(currentSessionId)
            } else null

            val allSummaries = sessionRepository.getAllSummaries()
            mutableUiState.update {
                val restoredText = updated?.currentTranscript ?: it.originalTranscript
                val wordCount = countWords(restoredText)
                it.copy(
                    transcript = restoredText,
                    isEdited = false,
                    isEditing = false,
                    metrics = it.metrics?.copy(wordCount = wordCount),
                    historySummaries = allSummaries,
                    filteredSummaries = sessionRepository.search(it.historySearchQuery),
                )
            }
        }
    }

    fun searchHistory(query: String) {
        val results = sessionRepository.search(query)
        mutableUiState.update {
            it.copy(
                historySearchQuery = query,
                filteredSummaries = results,
            )
        }
    }

    fun openSession(sessionId: String) {
        if (mutableUiState.value.phase == TranscriptionPhase.RECORDING ||
            mutableUiState.value.phase == TranscriptionPhase.TRANSCRIBING
        ) return

        worker.execute {
            val session = sessionRepository.getSession(sessionId) ?: return@execute
            val audio = sessionRepository.getAudio(sessionId)
            retainedAudio = audio
            retainedSource = session.title

            val metrics = BenchmarkMetrics(
                audioDurationSeconds = session.audioDurationSeconds,
                inferenceDurationSeconds = session.inferenceDurationSeconds,
                rtf = session.rtf,
                wordCount = session.wordCount,
                modelName = session.engine,
                backendInfo = "sherpa-onnx / CPU",
                deviceAbi = Build.SUPPORTED_ABIS.firstOrNull() ?: "arm64-v8a",
            )

            mutableUiState.update {
                it.copy(
                    phase = TranscriptionPhase.READY,
                    status = "Loaded: ${session.title}",
                    transcript = session.currentTranscript,
                    originalTranscript = session.originalTranscript,
                    isEdited = session.isEdited,
                    isEditing = false,
                    activeSessionId = session.id,
                    activeSessionTitle = session.title,
                    activeSessionRuns = session.runs,
                    metrics = metrics,
                    hasAudio = audio != null,
                    comparison = null,
                )
            }
        }
    }

    fun renameSession(sessionId: String, newTitle: String) {
        worker.execute {
            val updated = sessionRepository.renameSession(sessionId, newTitle)
            val allSummaries = sessionRepository.getAllSummaries()
            mutableUiState.update {
                it.copy(
                    activeSessionTitle = if (it.activeSessionId == sessionId) updated?.title ?: newTitle else it.activeSessionTitle,
                    historySummaries = allSummaries,
                    filteredSummaries = sessionRepository.search(it.historySearchQuery),
                )
            }
        }
    }

    fun deleteSession(sessionId: String) {
        worker.execute {
            sessionRepository.deleteSession(sessionId)
            val allSummaries = sessionRepository.getAllSummaries()
            mutableUiState.update {
                val isActive = it.activeSessionId == sessionId
                if (isActive) {
                    retainedAudio = null
                    retainedSource = ""
                    it.copy(
                        activeSessionId = null,
                        activeSessionTitle = null,
                        activeSessionRuns = emptyList(),
                        transcript = "",
                        originalTranscript = "",
                        isEdited = false,
                        isEditing = false,
                        metrics = null,
                        hasAudio = false,
                        status = "Ready — English, fully offline",
                        historySummaries = allSummaries,
                        filteredSummaries = sessionRepository.search(it.historySearchQuery),
                    )
                } else {
                    it.copy(
                        historySummaries = allSummaries,
                        filteredSummaries = sessionRepository.search(it.historySearchQuery),
                    )
                }
            }
        }
    }

    fun retranscribeSession(engineChoice: AsrEngineChoice, replaceCurrentTranscript: Boolean = false) {
        if (mutableUiState.value.phase != TranscriptionPhase.READY) return
        val audio = retainedAudio ?: return
        val sessionId = mutableUiState.value.activeSessionId
        val engineLabel = if (engineChoice == AsrEngineChoice.ZIPFORMER) "Zipformer" else "Moonshine Base"

        mutableUiState.update {
            it.copy(
                phase = TranscriptionPhase.TRANSCRIBING,
                status = "Retranscribing with $engineLabel…",
                metrics = null,
            )
        }

        worker.execute {
            try {
                val targetEngine = engineFor(engineChoice)
                val result = transcribeWithEngine(targetEngine, audio)
                val metrics = benchmarkMetrics(result, audio.durationSeconds)
                val now = System.currentTimeMillis()
                val run = TranscriptionRun(
                    id = "run_${now}_${engineChoice.name}",
                    engine = engineLabel,
                    hotwordsEnabled = hotwordsEnabled && engineChoice == AsrEngineChoice.ZIPFORMER,
                    transcript = result.text,
                    inferenceDurationSeconds = metrics.inferenceDurationSeconds,
                    rtf = metrics.rtf,
                    wordCount = metrics.wordCount,
                    timestamp = now,
                )

                if (sessionId != null) {
                    val updatedSession = sessionRepository.addRun(
                        id = sessionId,
                        run = run,
                        updateCurrentIfUnedited = replaceCurrentTranscript || !mutableUiState.value.isEdited,
                    )
                    val allSummaries = sessionRepository.getAllSummaries()
                    mutableUiState.update {
                        it.copy(
                            phase = TranscriptionPhase.READY,
                            status = if (replaceCurrentTranscript || !it.isEdited) {
                                "Retranscription complete"
                            } else {
                                "Retranscription complete (edits preserved)"
                            },
                            transcript = if (replaceCurrentTranscript || !it.isEdited) result.text else it.transcript,
                            originalTranscript = result.text,
                            isEdited = if (replaceCurrentTranscript) false else it.isEdited,
                            activeSessionRuns = updatedSession?.runs ?: (it.activeSessionRuns + run),
                            metrics = metrics,
                            hasAudio = true,
                            comparison = null,
                            historySummaries = allSummaries,
                            filteredSummaries = sessionRepository.search(it.historySearchQuery),
                        )
                    }
                } else {
                    mutableUiState.update {
                        it.copy(
                            phase = TranscriptionPhase.READY,
                            status = "Retranscription complete",
                            transcript = result.text,
                            originalTranscript = result.text,
                            isEdited = false,
                            metrics = metrics,
                            hasAudio = true,
                            comparison = null,
                        )
                    }
                }
            } catch (error: Throwable) {
                mutableUiState.update {
                    it.copy(
                        phase = TranscriptionPhase.READY,
                        status = "Retranscription failed: ${error.message ?: error.javaClass.simpleName}",
                    )
                }
            }
        }
    }

    fun retranscribe() {
        retranscribeSession(selectedEngine, replaceCurrentTranscript = !mutableUiState.value.isEdited)
    }

    fun clearTranscript() {
        if (mutableUiState.value.phase != TranscriptionPhase.READY) return
        retainedAudio = null
        retainedSource = ""
        mutableUiState.update {
            it.copy(
                transcript = "",
                originalTranscript = "",
                isEdited = false,
                isEditing = false,
                activeSessionId = null,
                activeSessionTitle = null,
                activeSessionRuns = emptyList(),
                metrics = null,
                hasAudio = false,
                status = "Ready — English, fully offline",
                comparison = null,
            )
        }
    }

    fun saveTranscript() {
        val state = mutableUiState.value
        val audio = retainedAudio
        val metrics = state.metrics
        if (state.phase != TranscriptionPhase.READY || audio == null || metrics == null) return

        val musicTreeUri = savedMusicTreeUri()
        if (musicTreeUri == null) {
            mutableUiState.update { it.copy(status = "Select the Music folder to enable saving.") }
            return
        }

        mutableUiState.update {
            it.copy(phase = TranscriptionPhase.SAVING, status = "Saving transcript, WAV, and metadata…")
        }
        worker.execute {
            try {
                val application = getApplication<Application>()
                val metadata = BenchmarkMetadata(
                    model = metrics.modelName,
                    backend = metrics.backendInfo,
                    audioDurationSeconds = metrics.audioDurationSeconds,
                    inferenceDurationSeconds = metrics.inferenceDurationSeconds,
                    rtf = metrics.rtf,
                    wordCount = countWords(state.transcript),
                    sampleRate = audio.sampleRate,
                    channels = audio.channels,
                    device = listOf(Build.MANUFACTURER, Build.MODEL)
                        .filter(String::isNotBlank)
                        .distinct()
                        .joinToString(" "),
                    deviceAbi = metrics.deviceAbi,
                    androidVersion = Build.VERSION.RELEASE,
                    appVersion = appVersion(application),
                    source = retainedSource,
                    isEdited = state.isEdited,
                    originalEngine = state.activeSessionRuns.firstOrNull()?.engine ?: metrics.modelName,
                )
                val relativeBase = saveBenchmarkBundleToMusic(
                    application = application,
                    transcript = state.transcript,
                    audio = audio,
                    metadata = metadata,
                    musicTreeUri = musicTreeUri,
                )
                mutableUiState.update {
                    it.copy(
                        phase = TranscriptionPhase.READY,
                        status = "Saved TXT, WAV, and JSON\n${relativeBase.substringAfterLast('/')}",
                    )
                }
            } catch (error: Throwable) {
                if (error is SecurityException) {
                    preferences.edit { remove(PREFERENCE_MUSIC_TREE_URI) }
                }
                mutableUiState.update {
                    it.copy(
                        phase = TranscriptionPhase.READY,
                        status = "Save failed: ${error.message ?: error.javaClass.simpleName}",
                    )
                }
            }
        }
    }

    fun hasMusicFolderAccess(): Boolean {
        val uri = savedMusicTreeUri() ?: return false
        return getApplication<Application>().contentResolver.persistedUriPermissions.any {
            it.uri == uri && it.isWritePermission
        }
    }

    fun registerMusicFolder(uri: Uri): Boolean {
        val application = getApplication<Application>()
        val folder = DocumentFile.fromTreeUri(application, uri)
        if (folder?.name != MUSIC_DIRECTORY_NAME || !folder.isDirectory) {
            mutableUiState.update {
                it.copy(status = "Please select the Music folder, then tap Use this folder.")
            }
            return false
        }

        return try {
            application.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
            )
            preferences.edit { putString(PREFERENCE_MUSIC_TREE_URI, uri.toString()) }
            true
        } catch (error: SecurityException) {
            mutableUiState.update {
                it.copy(status = "Could not keep access to Music: ${error.message ?: "permission denied"}")
            }
            false
        }
    }

    @RequiresPermission(Manifest.permission.RECORD_AUDIO)
    private fun captureAndTranscribe(recorder: AudioRecord) {
        val samples = ShortSampleBuffer()
        val input = ShortArray(READ_BUFFER_SAMPLES)
        val waveform = ArrayDeque<Float>(WAVEFORM_HISTORY_SIZE)
        var smoothedLevel = 0f
        var maxDurationReached = false
        var captureError: Throwable? = null
        val startedAt = SystemClock.elapsedRealtime()

        try {
            recorder.startRecording()
            check(recorder.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                "Microphone did not enter the recording state"
            }

            while (isRecording && !RecordingMetrics.reachedLimit(
                    startedAt,
                    SystemClock.elapsedRealtime(),
                    MAX_RECORDING_DURATION_MS,
                )) {
                val count = recorder.read(input, 0, input.size, AudioRecord.READ_BLOCKING)
                when {
                    count > 0 -> {
                        samples.append(input, count)
                        smoothedLevel = RecordingMetrics.smooth(
                            smoothedLevel,
                            RecordingMetrics.rmsLevel(input, count),
                        )
                        if (waveform.size == WAVEFORM_HISTORY_SIZE) waveform.removeFirst()
                        waveform.addLast(smoothedLevel)
                        val elapsed = (SystemClock.elapsedRealtime() - startedAt)
                            .coerceAtMost(MAX_RECORDING_DURATION_MS)
                        mutableUiState.update {
                            it.copy(
                                status = "Recording • ${RecordingMetrics.formatDuration(elapsed)}",
                                elapsedRecordingMs = elapsed,
                                waveform = waveform.toList(),
                            )
                        }
                        if (RecordingMetrics.reachedLimit(
                                startedAt,
                                SystemClock.elapsedRealtime(),
                                MAX_RECORDING_DURATION_MS,
                            )) {
                            maxDurationReached = true
                            isRecording = false
                        }
                    }
                    count < 0 && isRecording -> error("Microphone read failed with code $count")
                }
            }
            if (!isRecording && RecordingMetrics.reachedLimit(
                    startedAt,
                    SystemClock.elapsedRealtime(),
                    MAX_RECORDING_DURATION_MS,
                )) {
                maxDurationReached = true
            }
        } catch (error: Throwable) {
            if (isRecording) captureError = error
        } finally {
            isRecording = false
            if (recorder.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                try {
                    recorder.stop()
                } catch (_: IllegalStateException) {
                    // Already stopped by the UI thread.
                }
            }
            recorder.release()
            if (audioRecord === recorder) audioRecord = null
        }

        val error = captureError
        if (error != null) {
            mutableUiState.update {
                it.copy(
                    phase = TranscriptionPhase.READY,
                    status = "Recording failed: ${error.message ?: error.javaClass.simpleName}",
                )
            }
            return
        }

        val audio = PcmAudio(samples.toShortArray(), SAMPLE_RATE, 1)
        val titleDate = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date())
        transcribeOnWorker(
            audio = audio,
            source = "microphone",
            completionMessage = if (maxDurationReached) "Maximum 60-minute recording completed." else null,
            sessionTitle = "Recording – $titleDate",
            isImported = false,
        )
    }

    private fun transcribeOnWorker(
        audio: PcmAudio,
        source: String,
        completionMessage: String? = null,
        sessionTitle: String? = null,
        isImported: Boolean = false,
    ) {
        retainedAudio = audio
        retainedSource = source

        if (audio.frameCount == 0) {
            mutableUiState.update {
                it.copy(
                    phase = TranscriptionPhase.READY,
                    status = "No audio was recorded",
                    transcript = "",
                    originalTranscript = "",
                    metrics = null,
                    hasAudio = false,
                )
            }
            return
        }

        if (audio.durationSeconds < MIN_AUDIO_SECONDS) {
            mutableUiState.update {
                it.copy(
                    phase = TranscriptionPhase.READY,
                    status = String.format(
                        Locale.US,
                        "Audio is too short (%.2f s); record or import at least %.2f s",
                        audio.durationSeconds,
                        MIN_AUDIO_SECONDS,
                    ),
                    transcript = "",
                    originalTranscript = "",
                    metrics = null,
                    hasAudio = true,
                )
            }
            return
        }

        mutableUiState.update {
            it.copy(
                phase = TranscriptionPhase.TRANSCRIBING,
                status = listOfNotNull(completionMessage, "Transcribing offline…").joinToString(" "),
                hasAudio = true,
                waveform = emptyList(),
            )
        }

        try {
            val result = transcribeWithEngine(engineFor(selectedEngine), audio)
            val metrics = benchmarkMetrics(result, audio.durationSeconds)

            val session = if (sessionTitle != null && audio.frameCount > 0) {
                try {
                    sessionRepository.createSession(
                        audio = audio,
                        title = sessionTitle,
                        sourceType = if (isImported) SessionSource.IMPORTED else SessionSource.RECORDED,
                        transcript = result.text,
                        engine = if (selectedEngine == AsrEngineChoice.ZIPFORMER) "Zipformer" else "Moonshine Base",
                        hotwordsEnabled = hotwordsEnabled && selectedEngine == AsrEngineChoice.ZIPFORMER,
                        metrics = metrics,
                    )
                } catch (_: Throwable) {
                    null
                }
            } else null

            val allSummaries = sessionRepository.getAllSummaries()

            mutableUiState.update {
                it.copy(
                    phase = TranscriptionPhase.READY,
                    status = if (result.text.isEmpty()) {
                        "No speech detected — session saved to History"
                    } else {
                        "Ready — transcription complete"
                    },
                    transcript = result.text,
                    originalTranscript = result.text,
                    isEdited = false,
                    isEditing = false,
                    activeSessionId = session?.id,
                    activeSessionTitle = session?.title,
                    activeSessionRuns = session?.runs ?: emptyList(),
                    metrics = metrics,
                    hasAudio = true,
                    comparison = null,
                    historySummaries = allSummaries,
                    filteredSummaries = sessionRepository.search(it.historySearchQuery),
                )
            }
        } catch (error: Throwable) {
            mutableUiState.update {
                it.copy(
                    phase = TranscriptionPhase.READY,
                    status = "Transcription failed: ${error.message ?: error.javaClass.simpleName}",
                    transcript = "",
                    metrics = null,
                    hasAudio = true,
                )
            }
        }
    }

    override fun onCleared() {
        isRecording = false
        abandonAudioFocus()
        try {
            audioRecord?.stop()
        } catch (_: IllegalStateException) {
            // It was not started or has already stopped.
        }
        worker.execute {
            engine?.close()
            engine = null
            zipformerEngine?.close()
            zipformerEngine = null
        }
        worker.shutdown()
        super.onCleared()
    }

    fun onTrimMemory(level: Int) {
        // Release inactive engine when memory is tight to conserve RAM.
        worker.execute {
            if (selectedEngine != AsrEngineChoice.ZIPFORMER && zipformerEngine != null) {
                zipformerEngine?.close()
                zipformerEngine = null
            }
        }
    }

    private fun engineFor(choice: AsrEngineChoice): TranscriptionEngine = when (choice) {
        AsrEngineChoice.MOONSHINE -> checkNotNull(engine) { "Moonshine engine is unavailable" }
        AsrEngineChoice.ZIPFORMER -> zipformerEngine ?: ZipformerTranscriptionEngine(
            getApplication(),
            hotwordsEnabled,
        ).also { zipformerEngine = it }
    }

    private fun transcribeWithEngine(engine: TranscriptionEngine, audio: PcmAudio): TranscriptionResult = runBlocking {
        engine.transcribe(audio.toModelFloatArray())
    }

    private fun benchmarkMetrics(result: TranscriptionResult, audioDurationSeconds: Double): BenchmarkMetrics =
        BenchmarkMetrics(
            audioDurationSeconds = audioDurationSeconds,
            inferenceDurationSeconds = result.inferenceDurationMs / 1_000.0,
            rtf = calculateRtf(result.inferenceDurationMs, audioDurationSeconds),
            wordCount = countWords(result.text),
            modelName = result.modelName,
            backendInfo = result.backendInfo,
            deviceAbi = Build.SUPPORTED_ABIS.firstOrNull() ?: "unknown",
        )

    private fun saveComparisonToMusic(
        application: Application,
        comparison: ComparisonResult,
        musicTreeUri: Uri,
    ): String {
        val musicDirectory = checkNotNull(DocumentFile.fromTreeUri(application, musicTreeUri)) {
            "Music folder access is unavailable"
        }
        val appDirectoryName = application.getString(R.string.app_name)
        val appDirectory = musicDirectory.findFile(appDirectoryName)?.takeIf { it.isDirectory }
            ?: checkNotNull(musicDirectory.createDirectory(appDirectoryName)) {
                "Could not create $appDirectoryName in Music"
            }
        val baseName = "comparison_${FILE_TIME_FORMAT.format(LocalDateTime.now())}"
        val file = checkNotNull(appDirectory.createFile("application/json", "$baseName.json")) {
            "Could not create comparison JSON"
        }
        checkNotNull(application.contentResolver.openOutputStream(file.uri, "w")) {
            "Could not open comparison JSON destination"
        }.bufferedWriter(Charsets.UTF_8).use { writer ->
            writer.write(comparison.toJson())
            writer.newLine()
        }
        return "$baseName.json"
    }

    private fun saveBenchmarkBundleToMusic(
        application: Application,
        transcript: String,
        audio: PcmAudio,
        metadata: BenchmarkMetadata,
        musicTreeUri: Uri,
    ): String {
        val baseName = "transcript_${FILE_TIME_FORMAT.format(LocalDateTime.now())}"
        val appDirectoryName = application.getString(R.string.app_name)
        val musicDirectory = checkNotNull(DocumentFile.fromTreeUri(application, musicTreeUri)) {
            "Music folder access is unavailable"
        }
        check(musicDirectory.canWrite()) { "Music folder is not writable" }

        val appDirectory = musicDirectory.findFile(appDirectoryName)?.takeIf { it.isDirectory }
            ?: checkNotNull(musicDirectory.createDirectory(appDirectoryName)) {
                "Could not create $appDirectoryName in Music"
            }
        val plannedNames = listOf("$baseName.txt", "$baseName.wav", "$baseName.json")
        check(plannedNames.none { appDirectory.findFile(it) != null }) {
            "Files with this timestamp already exist; wait one second and save again"
        }

        // Create all three siblings under one timestamp. If any write fails,
        // delete only files created by this attempt so partial bundles do not
        // look like valid benchmark records.
        val createdFiles = ArrayList<DocumentFile>(3)
        try {
            val textFile = createFile(appDirectory, "text/plain", plannedNames[0], createdFiles)
            checkNotNull(application.contentResolver.openOutputStream(textFile.uri, "w")) {
                "Could not open the transcript destination"
            }.bufferedWriter(Charsets.UTF_8).use { writer ->
                writer.write(transcript)
                writer.newLine()
            }

            val wavFile = createFile(appDirectory, "audio/wav", plannedNames[1], createdFiles)
            checkNotNull(application.contentResolver.openOutputStream(wavFile.uri, "w")) {
                "Could not open the WAV destination"
            }.buffered().use { output -> WavCodec.write(audio, output) }

            val jsonFile = createFile(appDirectory, "application/json", plannedNames[2], createdFiles)
            checkNotNull(application.contentResolver.openOutputStream(jsonFile.uri, "w")) {
                "Could not open the metadata destination"
            }.bufferedWriter(Charsets.UTF_8).use { writer ->
                writer.write(metadata.toJson())
                writer.newLine()
            }
        } catch (error: Throwable) {
            createdFiles.asReversed().forEach(DocumentFile::delete)
            throw error
        }

        return "$MUSIC_DIRECTORY_NAME/$appDirectoryName/$baseName"
    }

    private fun createFile(
        directory: DocumentFile,
        mimeType: String,
        displayName: String,
        createdFiles: MutableList<DocumentFile>,
    ): DocumentFile {
        val file = checkNotNull(directory.createFile(mimeType, displayName)) {
            "Could not create $displayName"
        }
        createdFiles += file
        return file
    }

    private fun savedMusicTreeUri(): Uri? = preferences
        .getString(PREFERENCE_MUSIC_TREE_URI, null)
        ?.toUri()

    private fun queryDisplayName(context: Context, uri: Uri): String? {
        return try {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (idx >= 0) cursor.getString(idx) else null
                } else null
            }
        } catch (_: Throwable) {
            null
        }
    }

    @Suppress("DEPRECATION")
    private fun appVersion(application: Application): String = application.packageManager
        .getPackageInfo(application.packageName, 0)
        .versionName
        ?: "unknown"

    private class ShortSampleBuffer(initialCapacity: Int = SAMPLE_RATE * 10) {
        private var values = ShortArray(initialCapacity)
        var size: Int = 0
            private set

        fun append(source: ShortArray, count: Int) {
            ensureCapacity(size + count)
            source.copyInto(values, destinationOffset = size, startIndex = 0, endIndex = count)
            size += count
        }

        fun toShortArray(): ShortArray = values.copyOf(size)

        private fun ensureCapacity(required: Int) {
            if (required <= values.size) return
            values = values.copyOf(max(required, values.size * 2))
        }
    }

    private companion object {
        const val SAMPLE_RATE = WavCodec.MODEL_SAMPLE_RATE
        const val READ_BUFFER_SAMPLES = 512
        const val MAX_RECORDING_DURATION_MS = 60 * 60 * 1000L
        const val WAVEFORM_HISTORY_SIZE = 64
        const val MIN_AUDIO_SECONDS = 0.25
        const val PREFERENCES_NAME = "speech2text_settings"
        const val PREFERENCE_MUSIC_TREE_URI = "music_tree_uri"
        const val PREFERENCE_ENGINE = "asr_engine"
        const val PREFERENCE_HOTWORDS = "zipformer_hotwords"
        const val PREFERENCE_FONT = "app_font"
        const val MUSIC_DIRECTORY_NAME = "Music"
        val FILE_TIME_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss")
    }
}
