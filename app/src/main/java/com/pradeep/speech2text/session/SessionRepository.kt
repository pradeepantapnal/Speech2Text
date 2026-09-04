package com.pradeep.speech2text.session

import com.pradeep.speech2text.BenchmarkMetrics
import com.pradeep.speech2text.PcmAudio
import com.pradeep.speech2text.WavCodec
import com.pradeep.speech2text.countWords
import java.io.File
import java.util.UUID

/**
 * Local file-based repository for V0.5 sessions and audio persistence.
 *
 * All session data is stored completely locally in app-private storage:
 * - `<baseDir>/sessions/index.json`: Lightweight summary list for instant History rendering.
 * - `<baseDir>/sessions/<sessionId>/session.json`: Full session metadata and run history.
 * - `<baseDir>/sessions/<sessionId>/audio.wav`: Retained 16 kHz PCM WAV audio.
 *
 * Privacy guarantee: 100% local, zero network, zero cloud synchronization.
 */
class SessionRepository(private val baseDir: File) {

    private val sessionsDir = File(baseDir, "sessions").apply { mkdirs() }
    private val indexFile = File(sessionsDir, "index.json")
    private val lock = Any()

    /**
     * Cache of full session objects to avoid re-reading disk unnecessarily during searches or repeated opens.
     */
    private val sessionCache = mutableMapOf<String, TranscriptionSession>()

    init {
        synchronized(lock) {
            if (!indexFile.exists()) {
                rebuildIndexLocked()
            }
        }
    }

    /**
     * Returns lightweight summaries of all sessions, sorted newest first.
     */
    fun getAllSummaries(): List<SessionSummary> = synchronized(lock) {
        if (!indexFile.exists()) {
            rebuildIndexLocked()
        }
        val summaries = try {
            SessionJson.parseIndex(indexFile.readText(Charsets.UTF_8))
        } catch (_: Throwable) {
            rebuildIndexLocked()
        }
        summaries.sortedByDescending { it.createdAt }
    }

    /**
     * Searches sessions by case-insensitive substring match across title and transcript.
     */
    fun search(query: String): List<SessionSummary> = synchronized(lock) {
        val all = getAllSummaries()
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return all

        all.filter { summary ->
            if (summary.title.contains(trimmed, ignoreCase = true)) {
                return@filter true
            }
            // Check transcript text from cache or disk
            val session = getSessionLocked(summary.id)
            session != null && session.currentTranscript.contains(trimmed, ignoreCase = true)
        }
    }

    /**
     * Loads the full session metadata by ID.
     */
    fun getSession(id: String): TranscriptionSession? = synchronized(lock) {
        getSessionLocked(id)
    }

    private fun getSessionLocked(id: String): TranscriptionSession? {
        sessionCache[id]?.let { return it }
        val sessionFile = File(File(sessionsDir, id), "session.json")
        if (!sessionFile.exists()) return null
        return try {
            val session = SessionJson.parseSession(sessionFile.readText(Charsets.UTF_8))
            sessionCache[id] = session
            session
        } catch (_: Throwable) {
            null
        }
    }

    /**
     * Loads the retained audio WAV file for a session.
     */
    fun getAudio(sessionId: String): PcmAudio? = synchronized(lock) {
        val audioFile = File(File(sessionsDir, sessionId), "audio.wav")
        if (!audioFile.exists() || audioFile.length() == 0L) return null
        return try {
            audioFile.inputStream().use(WavCodec::read)
        } catch (_: Throwable) {
            null
        }
    }

    /**
     * Creates a new session from a completed transcription and saves its audio WAV.
     */
    fun createSession(
        audio: PcmAudio,
        title: String,
        sourceType: SessionSource,
        transcript: String,
        engine: String,
        hotwordsEnabled: Boolean,
        metrics: BenchmarkMetrics,
    ): TranscriptionSession = synchronized(lock) {
        val now = System.currentTimeMillis()
        val id = UUID.randomUUID().toString()
        val dir = File(sessionsDir, id).apply { mkdirs() }

        // 1. Write audio WAV
        val audioFile = File(dir, "audio.wav")
        val audioTmp = File(dir, "audio.wav.tmp")
        audioTmp.outputStream().use { stream ->
            WavCodec.write(audio, stream)
        }
        if (audioFile.exists()) {
            audioFile.delete()
        }
        audioTmp.renameTo(audioFile)

        // 2. Initial transcription run
        val initialRun = TranscriptionRun(
            id = "run_${now}_1",
            engine = engine,
            hotwordsEnabled = hotwordsEnabled,
            transcript = transcript,
            inferenceDurationSeconds = metrics.inferenceDurationSeconds,
            rtf = metrics.rtf,
            wordCount = metrics.wordCount,
            timestamp = now,
        )

        // 3. Build session
        val session = TranscriptionSession(
            id = id,
            createdAt = now,
            modifiedAt = now,
            title = title,
            sourceType = sourceType,
            audioRelativePath = "audio.wav",
            audioDurationSeconds = audio.durationSeconds,
            originalTranscript = transcript,
            currentTranscript = transcript,
            isEdited = false,
            engine = engine,
            inferenceDurationSeconds = metrics.inferenceDurationSeconds,
            rtf = metrics.rtf,
            wordCount = metrics.wordCount,
            hotwordsEnabled = hotwordsEnabled,
            runs = listOf(initialRun),
        )

        // 4. Save session.json
        saveSessionLocked(session)

        // 5. Update index
        val currentSummaries = getAllSummaries().filterNot { it.id == id }.toMutableList()
        currentSummaries.add(0, session.toSummary())
        saveIndexLocked(currentSummaries)

        session
    }

    /**
     * Updates the user-edited transcript for a session.
     * Preserves the originalTranscript untouched.
     */
    fun updateTranscript(id: String, newTranscript: String): TranscriptionSession? = synchronized(lock) {
        val existing = getSessionLocked(id) ?: return null
        val now = System.currentTimeMillis()
        val wordCount = countWords(newTranscript)
        val isEdited = newTranscript != existing.originalTranscript

        val updated = existing.copy(
            currentTranscript = newTranscript,
            isEdited = isEdited,
            wordCount = wordCount,
            modifiedAt = now,
        )

        saveSessionLocked(updated)
        updateSummaryInIndexLocked(updated.toSummary())
        updated
    }

    /**
     * Restores the transcript to its original raw ASR output.
     */
    fun restoreOriginalTranscript(id: String): TranscriptionSession? = synchronized(lock) {
        val existing = getSessionLocked(id) ?: return null
        val now = System.currentTimeMillis()
        val wordCount = countWords(existing.originalTranscript)

        val updated = existing.copy(
            currentTranscript = existing.originalTranscript,
            isEdited = false,
            wordCount = wordCount,
            modifiedAt = now,
        )

        saveSessionLocked(updated)
        updateSummaryInIndexLocked(updated.toSummary())
        updated
    }

    /**
     * Renames a session.
     */
    fun renameSession(id: String, newTitle: String): TranscriptionSession? = synchronized(lock) {
        val existing = getSessionLocked(id) ?: return null
        val now = System.currentTimeMillis()

        val updated = existing.copy(
            title = newTitle.trim().ifEmpty { existing.title },
            modifiedAt = now,
        )

        saveSessionLocked(updated)
        updateSummaryInIndexLocked(updated.toSummary())
        updated
    }

    /**
     * Adds a new transcription run (e.g. from Retranscribe or Compare) to the session.
     *
     * @param updateCurrentIfUnedited If true and the user has not manually edited the transcript,
     * updates currentTranscript and originalTranscript to the new run's output.
     */
    fun addRun(
        id: String,
        run: TranscriptionRun,
        updateCurrentIfUnedited: Boolean = true,
    ): TranscriptionSession? = synchronized(lock) {
        val existing = getSessionLocked(id) ?: return null
        val now = System.currentTimeMillis()
        val updatedRuns = existing.runs + run

        val shouldUpdateCurrent = updateCurrentIfUnedited && !existing.isEdited

        val updated = existing.copy(
            modifiedAt = now,
            engine = run.engine,
            hotwordsEnabled = run.hotwordsEnabled,
            inferenceDurationSeconds = run.inferenceDurationSeconds,
            rtf = run.rtf,
            originalTranscript = run.transcript,
            currentTranscript = if (shouldUpdateCurrent) run.transcript else existing.currentTranscript,
            wordCount = if (shouldUpdateCurrent) run.wordCount else existing.wordCount,
            runs = updatedRuns,
        )

        saveSessionLocked(updated)
        updateSummaryInIndexLocked(updated.toSummary())
        updated
    }

    /**
     * Deletes a session from app history and removes its app-private audio/metadata files.
     * Does NOT delete user-exported files in the SAF Music directory.
     */
    fun deleteSession(id: String): Boolean = synchronized(lock) {
        sessionCache.remove(id)
        val dir = File(sessionsDir, id)
        val deleted = if (dir.exists()) dir.deleteRecursively() else true

        val currentSummaries = getAllSummaries().filterNot { it.id == id }
        saveIndexLocked(currentSummaries)
        deleted
    }

    private fun saveSessionLocked(session: TranscriptionSession) {
        sessionCache[session.id] = session
        val dir = File(sessionsDir, session.id).apply { mkdirs() }
        val target = File(dir, "session.json")
        atomicWriteText(target, SessionJson.sessionToJson(session))
    }

    private fun updateSummaryInIndexLocked(summary: SessionSummary) {
        val summaries = getAllSummaries().toMutableList()
        val index = summaries.indexOfFirst { it.id == summary.id }
        if (index >= 0) {
            summaries[index] = summary
        } else {
            summaries.add(0, summary)
        }
        saveIndexLocked(summaries)
    }

    private fun saveIndexLocked(summaries: List<SessionSummary>) {
        atomicWriteText(indexFile, SessionJson.indexToJson(summaries))
    }

    private fun atomicWriteText(target: File, content: String) {
        val tmp = File(target.parentFile, "${target.name}.tmp")
        tmp.writeText(content, Charsets.UTF_8)
        if (target.exists()) {
            target.delete()
        }
        tmp.renameTo(target)
    }

    private fun rebuildIndexLocked(): List<SessionSummary> {
        val sessionDirs = sessionsDir.listFiles { file -> file.isDirectory } ?: emptyArray()
        val summaries = mutableListOf<SessionSummary>()
        for (dir in sessionDirs) {
            val sessionFile = File(dir, "session.json")
            if (sessionFile.exists()) {
                try {
                    val session = SessionJson.parseSession(sessionFile.readText(Charsets.UTF_8))
                    summaries.add(session.toSummary())
                    sessionCache[session.id] = session
                } catch (_: Throwable) {
                    // Ignore corrupted session folder
                }
            }
        }
        val sorted = summaries.sortedByDescending { it.createdAt }
        saveIndexLocked(sorted)
        return sorted
    }
}
