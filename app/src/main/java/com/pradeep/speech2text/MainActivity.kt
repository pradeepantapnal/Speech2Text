package com.pradeep.speech2text

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Switch
import androidx.compose.material3.TextButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.Icon
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AudioFile
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.FiberManualRecord
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Save
import androidx.compose.material.icons.outlined.Stop
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material.icons.automirrored.outlined.CompareArrows
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import com.pradeep.speech2text.ui.theme.Speech2TextTheme
import java.util.Locale

private val INITIAL_MUSIC_FOLDER_URI: Uri =
    "content://com.android.externalstorage.documents/document/primary%3AMusic".toUri()

private val WAV_MIME_TYPES = arrayOf(
    "audio/wav",
    "audio/x-wav",
    "audio/wave",
    "audio/vnd.wave",
    "application/octet-stream",
)

class MainActivity : ComponentActivity() {
    private val transcriptionViewModel: TranscriptionViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            Speech2TextTheme {
                TranscriptionScreen(
                    viewModel = transcriptionViewModel,
                    hasMicrophonePermission = {
                        ContextCompat.checkSelfPermission(
                            this,
                            Manifest.permission.RECORD_AUDIO,
                        ) == PackageManager.PERMISSION_GRANTED
                    },
                )
            }
        }
    }

    override fun onStop() {
        // Never leave microphone capture running after the activity is hidden.
        transcriptionViewModel.stopRecording()
        window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        super.onStop()
    }

    override fun onDestroy() {
        window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        super.onDestroy()
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun TranscriptionScreen(
    viewModel: TranscriptionViewModel,
    hasMicrophonePermission: () -> Boolean,
) {
    val state by viewModel.uiState.collectAsState()
    val activity = LocalActivity.current
    SideEffect {
        if (state.phase == TranscriptionPhase.RECORDING) {
            activity?.window?.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } else {
            activity?.window?.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }
    var permissionDenied by remember { mutableStateOf(false) }
    var advancedOpen by remember { mutableStateOf(false) }
    var clearConfirmationOpen by remember { mutableStateOf(false) }
    var aboutOpen by remember { mutableStateOf(false) }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        permissionDenied = !granted
        if (granted) viewModel.startRecording()
    }
    val musicFolderLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree(),
    ) { uri ->
        if (uri != null && viewModel.registerMusicFolder(uri)) {
            viewModel.saveTranscript()
        }
    }
    val wavLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) viewModel.importWav(uri)
    }
    val displayedStatus = if (permissionDenied) {
        stringResource(R.string.microphone_permission_required)
    } else {
        state.status
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.surface,
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = stringResource(R.string.main_title),
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = stringResource(R.string.app_subtitle),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            StatusPanel(
                phase = state.phase,
                status = displayedStatus,
                permissionDenied = permissionDenied,
                elapsedMs = state.elapsedRecordingMs,
                waveform = state.waveform,
            )

            TranscriptPanel(
                transcript = state.transcript,
                modifier = Modifier.weight(1f),
            )

            PrimaryActionPanel(
                state = state,
                onRecord = {
                    permissionDenied = false
                    if (hasMicrophonePermission()) {
                        viewModel.startRecording()
                    } else {
                        permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                    }
                },
                onStop = viewModel::stopRecording,
                onSave = {
                    if (viewModel.hasMusicFolderAccess()) {
                        viewModel.saveTranscript()
                    } else {
                        musicFolderLauncher.launch(INITIAL_MUSIC_FOLDER_URI)
                    }
                },
                onAdvanced = { advancedOpen = true },
            )
        }
    }

    if (advancedOpen) {
        ModalBottomSheet(onDismissRequest = { advancedOpen = false }) {
            AdvancedSheetContent(
                state = state,
                onImport = { wavLauncher.launch(WAV_MIME_TYPES) },
                onClear = {
                    advancedOpen = false
                    clearConfirmationOpen = true
                },
                onSelectEngine = viewModel::selectEngine,
                onHotwordsChanged = viewModel::setHotwordsEnabled,
                onCompare = viewModel::compareRetainedAudio,
                onRetest = viewModel::retranscribe,
                onAbout = {
                    advancedOpen = false
                    aboutOpen = true
                },
            )
        }
    }

    if (clearConfirmationOpen) {
        AlertDialog(
            onDismissRequest = { clearConfirmationOpen = false },
            title = { Text("Clear current recording and transcript?") },
            text = { Text("This only clears the current app session. Saved Music files are not affected.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        clearConfirmationOpen = false
                        viewModel.clearTranscript()
                    },
                ) { Text("Clear", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { clearConfirmationOpen = false }) { Text("Cancel") } },
        )
    }

    if (aboutOpen) {
        AboutDialog(onDismiss = { aboutOpen = false })
    }
}

@Composable
private fun PrimaryActionPanel(
    state: TranscriptionUiState,
    onRecord: () -> Unit,
    onStop: () -> Unit,
    onSave: () -> Unit,
    onAdvanced: () -> Unit,
) {
    val recording = state.phase == TranscriptionPhase.RECORDING
    val busy = state.phase == TranscriptionPhase.TRANSCRIBING || state.phase == TranscriptionPhase.SAVING
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Button(
            onClick = if (recording) onStop else onRecord,
            enabled = state.phase == TranscriptionPhase.READY || recording,
            modifier = Modifier.fillMaxWidth().height(56.dp),
            shape = RoundedCornerShape(18.dp),
        ) {
            ActionLabel(
                icon = if (recording) Icons.Outlined.Stop else Icons.Outlined.FiberManualRecord,
                label = if (recording) "Stop" else "Record",
            )
        }
        OutlinedButton(
            onClick = onSave,
            enabled = state.hasAudio && state.metrics != null && state.phase == TranscriptionPhase.READY,
            modifier = Modifier.fillMaxWidth().height(54.dp),
            shape = RoundedCornerShape(18.dp),
        ) {
            ActionLabel(Icons.Outlined.Save, if (state.phase == TranscriptionPhase.SAVING) "Saving…" else "Save")
        }
        OutlinedButton(
            onClick = onAdvanced,
            enabled = !busy && state.phase != TranscriptionPhase.RECORDING,
            modifier = Modifier.fillMaxWidth().height(50.dp),
            shape = RoundedCornerShape(18.dp),
        ) {
            ActionLabel(Icons.Outlined.Settings, "Advanced")
            Icon(Icons.Outlined.ExpandMore, contentDescription = "Open Advanced")
        }
    }
}

@Composable
private fun AdvancedSheetContent(
    state: TranscriptionUiState,
    onImport: () -> Unit,
    onClear: () -> Unit,
    onSelectEngine: (AsrEngineChoice) -> Unit,
    onHotwordsChanged: (Boolean) -> Unit,
    onCompare: () -> Unit,
    onRetest: () -> Unit,
    onAbout: () -> Unit,
) {
    val ready = state.phase == TranscriptionPhase.READY
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(start = 20.dp, end = 20.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text("Advanced", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text(
            "Additional file, transcription, testing, and app options",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        AdvancedSectionLabel("FILE")
        AdvancedRow(Icons.Outlined.FolderOpen, "Import WAV", "Open a local WAV file", enabled = ready, onClick = onImport)
        AdvancedRow(
            Icons.Outlined.DeleteOutline,
            "Clear transcript and audio",
            "Remove current session data only",
            destructive = true,
            enabled = ready && state.hasAudio,
            onClick = onClear,
        )

        AdvancedSectionLabel("TRANSCRIPTION")
        Text("Engine", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 6.dp))
        Text(
            "Choose the offline recognizer used for Record, Import, and Retest",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AsrEngineChoice.entries.forEach { choice ->
                FilterChip(
                    selected = state.selectedEngine == choice,
                    onClick = { onSelectEngine(choice) },
                    enabled = ready,
                    label = { Text(if (choice == AsrEngineChoice.ZIPFORMER) "Zipformer" else "Moonshine") },
                )
            }
        }
        Text(
            if (state.selectedEngine == AsrEngineChoice.ZIPFORMER) {
                "Technical vocabulary / contextual hotwords"
            } else {
                "Default / accurate offline transcription"
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Technical hotwords", style = MaterialTheme.typography.titleSmall)
                Text(
                    "Bias Zipformer recognition toward technical terminology.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(
                checked = state.hotwordsEnabled,
                onCheckedChange = onHotwordsChanged,
                enabled = ready && state.selectedEngine == AsrEngineChoice.ZIPFORMER,
            )
        }

        AdvancedSectionLabel("TESTING")
        AdvancedRow(
            Icons.AutoMirrored.Outlined.CompareArrows,
            "Compare same WAV",
            "Run both engines and compare results",
            enabled = ready && state.hasAudio,
            onClick = onCompare,
        )
        AdvancedRow(
            Icons.Outlined.Refresh,
            "Retest current audio",
            "Run the selected engine again",
            enabled = ready && state.hasAudio,
            onClick = onRetest,
        )
        if (state.metrics != null || state.comparison != null) {
            Text("Benchmark details", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 8.dp))
            state.metrics?.let { BenchmarkPanel(it) }
            state.comparison?.let { ComparisonPanel(it) }
        } else {
            AdvancedRow(Icons.Outlined.BarChart, "Benchmark details", "No benchmark available yet", enabled = false) {}
        }

        AdvancedSectionLabel("APP")
        AdvancedRow(Icons.Outlined.Info, "About", "About this app and developer", onClick = onAbout)
    }
}

@Composable
private fun AdvancedSectionLabel(label: String) {
    Text(
        text = label,
        modifier = Modifier.padding(top = 18.dp, bottom = 4.dp),
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary,
    )
}

@Composable
private fun AdvancedRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    description: String,
    destructive: Boolean = false,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    val contentColor = if (destructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onClick)
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = title, tint = if (enabled) contentColor else contentColor.copy(alpha = 0.4f), modifier = Modifier.size(26.dp))
        Column(modifier = Modifier.weight(1f).padding(horizontal = 14.dp)) {
            Text(title, color = if (enabled) contentColor else contentColor.copy(alpha = 0.4f), style = MaterialTheme.typography.bodyLarge)
            Text(description, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = if (enabled) 1f else 0.45f), style = MaterialTheme.typography.bodySmall)
        }
        Icon(Icons.Outlined.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f))
}

@Composable
private fun AboutDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
        title = { Text("Pradeep Speech2Text") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Private, fully offline speech transcription for Android.")
                Text("Developed by:\nPradeep\n\nSenior Principal Engineer")
                Text("Focus areas:\nAndroid & Linux Platform Engineering\nEmbedded Systems\nSystem Architecture")
                Text("Privacy:\nAudio and transcripts are processed locally on the device. The application does not require Internet access.")
                Text("Version 0.4\nMoonshine Base and Zipformer via sherpa-onnx")
            }
        },
    )
}

@Composable
private fun EngineControls(
    state: TranscriptionUiState,
    onSelect: (AsrEngineChoice) -> Unit,
    onHotwordsChanged: (Boolean) -> Unit,
    onCompare: () -> Unit,
) {
    val ready = state.phase == TranscriptionPhase.READY
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = "Engine",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            AsrEngineChoice.entries.forEach { choice ->
                FilterChip(
                    selected = state.selectedEngine == choice,
                    onClick = { onSelect(choice) },
                    enabled = ready,
                    label = { Text(choice.label, maxLines = 1) },
                )
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (state.selectedEngine == AsrEngineChoice.ZIPFORMER) {
                Text(
                    text = "Technical hotwords",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                Switch(
                    checked = state.hotwordsEnabled,
                    onCheckedChange = onHotwordsChanged,
                    enabled = ready,
                )
            } else {
                Spacer(modifier = Modifier.weight(1f))
            }
            if (state.hasAudio && ready) {
                TextButton(onClick = onCompare) {
                    Text("Compare")
                }
            }
        }
    }
}

@Composable
private fun ComparisonPanel(comparison: ComparisonResult) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.28f),
        ),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("Same-WAV comparison", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
            ComparisonEntry("Moonshine Base", comparison.moonshine, comparison.moonshineText, false)
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            ComparisonEntry("Zipformer / Transducer", comparison.transducer, comparison.transducerText, comparison.hotwordsEnabled)
        }
    }
}

@Composable
private fun ComparisonEntry(
    title: String,
    metrics: BenchmarkMetrics,
    transcript: String,
    hotwordsEnabled: Boolean,
) {
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
        Text(
            text = "RTF: ${String.format(Locale.US, "%.3f", metrics.rtf)} • ${String.format(Locale.US, "%.2f", metrics.inferenceDurationSeconds)} s • ${metrics.wordCount} words" +
                if (title.startsWith("Zipformer")) " • Hotwords: ${if (hotwordsEnabled) "ON" else "OFF"}" else "",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = transcript.ifBlank { "No speech detected" },
            style = MaterialTheme.typography.bodySmall,
            maxLines = 5,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun BenchmarkPanel(metrics: BenchmarkMetrics) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.28f),
        ),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = stringResource(R.string.benchmark),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
            )
            Row(modifier = Modifier.fillMaxWidth()) {
                MetricValue(
                    label = stringResource(R.string.audio_metric),
                    value = stringResource(R.string.seconds_value, metrics.audioDurationSeconds),
                    modifier = Modifier.weight(1f),
                )
                MetricValue(
                    label = stringResource(R.string.inference_metric),
                    value = stringResource(R.string.seconds_value, metrics.inferenceDurationSeconds),
                    modifier = Modifier.weight(1f),
                )
                MetricValue(
                    label = stringResource(R.string.rtf_metric),
                    value = stringResource(R.string.rtf_value, metrics.rtf),
                    modifier = Modifier.weight(1f),
                )
                MetricValue(
                    label = stringResource(R.string.words_metric),
                    value = metrics.wordCount.toString(),
                    modifier = Modifier.weight(1f),
                )
            }
            Text(
                text = stringResource(R.string.model_and_abi, metrics.modelName, metrics.deviceAbi),
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = stringResource(R.string.backend_detail, metrics.backendInfo),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun MetricValue(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun StatusPanel(
    phase: TranscriptionPhase,
    status: String,
    permissionDenied: Boolean,
    elapsedMs: Long = 0L,
    waveform: List<Float> = emptyList(),
) {
    val isError = phase == TranscriptionPhase.ERROR || permissionDenied
    val accent = when {
        isError -> MaterialTheme.colorScheme.error
        phase == TranscriptionPhase.RECORDING -> Color(0xFFFF3B4F)
        phase == TranscriptionPhase.READY -> Color(0xFF35D07F)
        phase == TranscriptionPhase.TRANSCRIBING || phase == TranscriptionPhase.SAVING ->
            MaterialTheme.colorScheme.tertiary
        else -> MaterialTheme.colorScheme.primary
    }
    val isRecording = phase == TranscriptionPhase.RECORDING
    val title = when {
        isRecording -> "Recording"
        phase == TranscriptionPhase.READY -> "Ready"
        else -> status
    }
    val subtitle = when {
        isRecording -> stringResource(R.string.microphone_active)
        phase == TranscriptionPhase.READY -> "English • fully offline"
        else -> stringResource(R.string.offline_model)
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.62f),
        shape = RoundedCornerShape(22.dp),
        tonalElevation = 3.dp,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .shadow(8.dp, CircleShape, clip = false)
                    .clip(CircleShape)
                    .background(accent.copy(alpha = 0.18f))
                    .padding(6.dp)
                    .clip(CircleShape)
                    .background(accent),
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 12.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (isRecording) {
                AudioLevelMeter(waveform.lastOrNull() ?: 0f)
                Text(
                    text = RecordingMetrics.formatDuration(elapsedMs),
                    modifier = Modifier
                        .padding(start = 8.dp)
                        .wrapContentWidth(),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    softWrap = false,
                )
            }
        }
    }
}

@Composable
private fun AudioLevelMeter(level: Float) {
    val green = Color(0xFF35D07F)
    val yellow = Color(0xFFFFD166)
    val orange = Color(0xFFFF9F43)
    val red = Color(0xFFFF3B4F)
    androidx.compose.foundation.Canvas(
        modifier = Modifier
            .padding(start = 10.dp)
            .size(width = 98.dp, height = 28.dp),
    ) {
        val barCount = 10
        val gap = 2.dp.toPx()
        val barWidth = 8.dp.toPx()
        val activeBars = (level.coerceIn(0f, 1f) * barCount).toInt().coerceIn(1, barCount)
        repeat(barCount) { index ->
            val color = when {
                index >= 9 -> red
                index >= 8 -> orange
                index >= 6 -> yellow
                else -> green
            }
            val height = (8.dp.toPx() + (index + 1) * 1.8.dp.toPx())
            drawRoundRect(
                color = color.copy(alpha = if (index < activeBars) 1f else 0.18f),
                topLeft = Offset(index * (barWidth + gap), size.height - height),
                size = Size(barWidth, height),
                cornerRadius = CornerRadius(barWidth, barWidth),
            )
        }
    }
}

@Composable
private fun TranscriptPanel(
    transcript: String,
    modifier: Modifier = Modifier,
) {
    val wordCount = remember(transcript) {
        transcript.trim().takeIf(String::isNotEmpty)?.split(Regex("\\s+"))?.size ?: 0
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.38f),
        ),
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.transcript),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
                Surface(
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                    shape = RoundedCornerShape(50),
                ) {
                    Text(
                        text = pluralStringResource(R.plurals.word_count, wordCount, wordCount),
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Column(
                modifier = Modifier
                    .fillMaxSize()
                .verticalScroll(rememberScrollState())
                    .padding(20.dp),
            ) {
                if (transcript.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(14.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Description,
                                contentDescription = null,
                                modifier = Modifier.size(52.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f),
                            )
                            Text(
                                text = stringResource(R.string.transcript_empty),
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center,
                            )
                        }
                    }
                } else {
                    SelectionContainer {
                        Text(
                            text = transcript,
                            style = MaterialTheme.typography.bodyLarge,
                            lineHeight = 27.sp,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ActionPanel(
    state: TranscriptionUiState,
    onRecord: () -> Unit,
    onStop: () -> Unit,
    onImport: () -> Unit,
    onRetranscribe: () -> Unit,
    onSave: () -> Unit,
    onClear: () -> Unit,
) {
    val isBusy = state.phase == TranscriptionPhase.RECORDING ||
        state.phase == TranscriptionPhase.TRANSCRIBING ||
        state.phase == TranscriptionPhase.SAVING

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Button(
                onClick = if (state.phase == TranscriptionPhase.RECORDING) onStop else onRecord,
                enabled = state.phase == TranscriptionPhase.READY || state.phase == TranscriptionPhase.RECORDING,
                modifier = Modifier
                    .weight(1f)
                    .height(52.dp),
                shape = RoundedCornerShape(16.dp),
                contentPadding = PaddingValues(horizontal = 6.dp),
            ) {
                ActionLabel(
                    icon = if (state.phase == TranscriptionPhase.RECORDING) Icons.Outlined.Stop else Icons.Outlined.FiberManualRecord,
                    label = stringResource(
                        if (state.phase == TranscriptionPhase.RECORDING) R.string.stop else R.string.record,
                    ),
                )
            }
            OutlinedButton(
                onClick = onImport,
                enabled = state.phase == TranscriptionPhase.READY,
                modifier = Modifier
                    .weight(1f)
                    .height(52.dp),
                shape = RoundedCornerShape(16.dp),
                contentPadding = PaddingValues(horizontal = 6.dp),
            ) {
                ActionLabel(Icons.Outlined.AudioFile, stringResource(R.string.import_wav))
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            OutlinedButton(
                onClick = onRetranscribe,
                enabled = state.hasAudio && state.phase == TranscriptionPhase.READY,
                modifier = Modifier
                    .weight(1f)
                    .height(52.dp),
                shape = RoundedCornerShape(16.dp),
                contentPadding = PaddingValues(horizontal = 6.dp),
            ) {
                ActionLabel(Icons.Outlined.Refresh, stringResource(R.string.retest))
            }
            OutlinedButton(
                onClick = onSave,
                enabled = state.hasAudio && state.metrics != null && state.phase == TranscriptionPhase.READY,
                modifier = Modifier
                    .weight(1f)
                    .height(52.dp),
                shape = RoundedCornerShape(16.dp),
                contentPadding = PaddingValues(horizontal = 6.dp),
            ) {
                ActionLabel(
                    icon = Icons.Outlined.Save,
                    label =
                    if (state.phase == TranscriptionPhase.SAVING) {
                        stringResource(R.string.saving)
                    } else {
                        stringResource(R.string.save)
                    },
                )
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            OutlinedButton(
                onClick = onClear,
                enabled = (state.transcript.isNotEmpty() || state.hasAudio) && !isBusy,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(16.dp),
                contentPadding = PaddingValues(horizontal = 6.dp),
            ) {
                ActionLabel(Icons.Outlined.DeleteOutline, stringResource(R.string.clear))
            }
        }
    }
}

@Composable
private fun ActionLabel(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        Icon(imageVector = icon, contentDescription = null, modifier = Modifier.size(22.dp))
        Text(label)
    }
}
