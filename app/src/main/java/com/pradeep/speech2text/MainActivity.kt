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
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.Icon
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AudioFile
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.FiberManualRecord
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Save
import androidx.compose.material.icons.outlined.Stop
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material.icons.automirrored.outlined.CompareArrows
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.Autorenew
import androidx.compose.material.icons.outlined.Clear
import androidx.compose.material.icons.outlined.Compare
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Search
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.width
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.ui.graphics.SolidColor
import com.pradeep.speech2text.session.SessionSource
import com.pradeep.speech2text.session.SessionSummary
import com.pradeep.speech2text.session.TranscriptionRun
import com.pradeep.speech2text.session.TranscriptionSession
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
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
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
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
import android.widget.Toast
import com.pradeep.speech2text.ui.theme.AppFont
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

private enum class AppSheet { ADVANCED, HISTORY }

class MainActivity : ComponentActivity() {
    private val transcriptionViewModel: TranscriptionViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            val state by transcriptionViewModel.uiState.collectAsState()
            Speech2TextTheme(appFont = state.appFont) {
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

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        transcriptionViewModel.onTrimMemory(level)
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
    var activeSheet by remember { mutableStateOf<AppSheet?>(null) }
    var clearConfirmationOpen by remember { mutableStateOf(false) }
    var aboutOpen by remember { mutableStateOf(false) }
    var originalTranscriptOpen by remember { mutableStateOf(false) }
    var retranscribeOpen by remember { mutableStateOf(false) }
    var sessionDetailsTarget by remember { mutableStateOf<TranscriptionSession?>(null) }
    var renameSessionTarget by remember { mutableStateOf<SessionSummary?>(null) }
    var deleteSessionTarget by remember { mutableStateOf<SessionSummary?>(null) }
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
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val displayedStatus = if (permissionDenied) {
        stringResource(R.string.microphone_permission_required)
    } else {
        state.status
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = stringResource(R.string.main_title),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            text = state.activeSessionTitle ?: stringResource(R.string.app_subtitle),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { activeSheet = AppSheet.ADVANCED }) {
                        Icon(
                            imageVector = Icons.Outlined.Settings,
                            contentDescription = "Settings & Advanced",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            StatusPanel(
                phase = state.phase,
                status = displayedStatus,
                permissionDenied = permissionDenied,
                elapsedMs = state.elapsedRecordingMs,
                waveform = state.waveform,
            )

            TranscriptPanel(
                transcript = state.transcript,
                isEdited = state.isEdited,
                isEditing = state.isEditing,
                canEdit = state.phase == TranscriptionPhase.READY && state.transcript.isNotEmpty(),
                onStartEdit = viewModel::startEditingTranscript,
                onCancelEdit = viewModel::cancelEditingTranscript,
                onSaveEdit = viewModel::saveTranscriptEdit,
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
                onImport = { wavLauncher.launch(WAV_MIME_TYPES) },
                onCopy = {
                    if (state.transcript.isNotEmpty()) {
                        clipboardManager.setText(AnnotatedString(state.transcript))
                        Toast.makeText(context, "Transcript copied", Toast.LENGTH_SHORT).show()
                    }
                },
                onSave = {
                    if (viewModel.hasMusicFolderAccess()) {
                        viewModel.saveTranscript()
                    } else {
                        musicFolderLauncher.launch(INITIAL_MUSIC_FOLDER_URI)
                    }
                },
            )
        }
    }

    activeSheet?.let { sheet ->
        ModalBottomSheet(onDismissRequest = { activeSheet = null }) {
            when (sheet) {
                AppSheet.ADVANCED -> {
                    AdvancedSheetContent(
                        state = state,
                        onHistory = { activeSheet = AppSheet.HISTORY },
                        onViewOriginal = {
                            activeSheet = null
                            originalTranscriptOpen = true
                        },
                        onViewSessionDetails = {
                            val currentId = state.activeSessionId
                            if (currentId != null) {
                                sessionDetailsTarget = viewModel.sessionRepository.getSession(currentId)
                            }
                        },
                        onRetranscribeSession = {
                            activeSheet = null
                            retranscribeOpen = true
                        },
                        onImport = { wavLauncher.launch(WAV_MIME_TYPES) },
                        onClear = {
                            activeSheet = null
                            clearConfirmationOpen = true
                        },
                        onSelectEngine = viewModel::selectEngine,
                        onHotwordsChanged = viewModel::setHotwordsEnabled,
                        onSelectFont = viewModel::selectFont,
                        onCompare = viewModel::compareRetainedAudio,
                        onRetest = viewModel::retranscribe,
                        onAbout = {
                            activeSheet = null
                            aboutOpen = true
                        },
                    )
                }
                AppSheet.HISTORY -> {
                    HistorySheet(
                        summaries = state.filteredSummaries,
                        searchQuery = state.historySearchQuery,
                        onSearchChange = viewModel::searchHistory,
                        onOpenSession = { id ->
                            viewModel.openSession(id)
                            activeSheet = null
                        },
                        onRenameSession = { renameSessionTarget = it },
                        onDeleteSession = { deleteSessionTarget = it },
                        onViewDetails = { summary ->
                            val session = viewModel.sessionRepository.getSession(summary.id)
                            if (session != null) {
                                sessionDetailsTarget = session
                            }
                        },
                        onBack = { activeSheet = AppSheet.ADVANCED },
                        onDismiss = { activeSheet = null },
                    )
                }
            }
        }
    }

    renameSessionTarget?.let { summary ->
        RenameSessionDialog(
            initialTitle = summary.title,
            onConfirm = { newTitle ->
                viewModel.renameSession(summary.id, newTitle)
                renameSessionTarget = null
            },
            onDismiss = { renameSessionTarget = null },
        )
    }

    deleteSessionTarget?.let { summary ->
        DeleteSessionDialog(
            summary = summary,
            onConfirm = {
                viewModel.deleteSession(summary.id)
                deleteSessionTarget = null
            },
            onDismiss = { deleteSessionTarget = null },
        )
    }

    if (originalTranscriptOpen) {
        OriginalTranscriptDialog(
            originalTranscript = state.originalTranscript,
            onRestore = viewModel::restoreOriginalTranscript,
            onDismiss = { originalTranscriptOpen = false },
        )
    }

    if (retranscribeOpen) {
        RetranscribeDialog(
            currentEngine = state.selectedEngine,
            hotwordsEnabled = state.hotwordsEnabled,
            isEdited = state.isEdited,
            onRetranscribe = { engine, replaceCurrent ->
                viewModel.retranscribeSession(engine, replaceCurrent)
            },
            onDismiss = { retranscribeOpen = false },
        )
    }

    sessionDetailsTarget?.let { session ->
        SessionDetailsDialog(
            session = session,
            onDismiss = { sessionDetailsTarget = null },
        )
    }

    if (clearConfirmationOpen) {
        AlertDialog(
            onDismissRequest = { clearConfirmationOpen = false },
            title = { Text("Clear current screen?") },
            text = {
                Text(
                    "This only unloads the current session from the screen so you can start a new recording.\n\n" +
                    "The session remains saved in History, and exported Music files are not affected."
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        clearConfirmationOpen = false
                        viewModel.clearTranscript()
                    },
                ) { Text("Clear Screen", color = MaterialTheme.colorScheme.error) }
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
    onImport: () -> Unit,
    onCopy: () -> Unit,
    onSave: () -> Unit,
) {
    val recording = state.phase == TranscriptionPhase.RECORDING
    val busy = state.phase == TranscriptionPhase.TRANSCRIBING || state.phase == TranscriptionPhase.SAVING
    val hasText = state.transcript.isNotEmpty()
    val canSave = state.hasAudio && state.metrics != null && state.phase == TranscriptionPhase.READY

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Left action: Import WAV
        FilledTonalIconButton(
            onClick = onImport,
            enabled = state.phase == TranscriptionPhase.READY,
            modifier = Modifier.size(52.dp),
        ) {
            Icon(
                imageVector = Icons.Outlined.FolderOpen,
                contentDescription = "Import WAV",
                modifier = Modifier.size(24.dp),
            )
        }

        // Center Primary Action: Large Voice Recorder Mic Button
        FloatingActionButton(
            onClick = if (recording) onStop else onRecord,
            modifier = Modifier.size(76.dp),
            shape = CircleShape,
            containerColor = if (recording) Color(0xFFFF3B4F) else MaterialTheme.colorScheme.primaryContainer,
            contentColor = if (recording) Color.White else MaterialTheme.colorScheme.onPrimaryContainer,
        ) {
            if (recording) {
                Icon(
                    imageVector = Icons.Outlined.Stop,
                    contentDescription = "Stop",
                    modifier = Modifier.size(36.dp),
                )
            } else {
                Icon(
                    imageVector = Icons.Outlined.Mic,
                    contentDescription = "Record",
                    modifier = Modifier.size(36.dp),
                )
            }
        }

        // Right action 1: Copy transcript
        FilledTonalIconButton(
            onClick = onCopy,
            enabled = hasText && !busy,
            modifier = Modifier.size(52.dp),
        ) {
            Icon(
                imageVector = Icons.Outlined.ContentCopy,
                contentDescription = "Copy Transcript",
                modifier = Modifier.size(24.dp),
            )
        }

        // Right action 2: Save to storage
        FilledTonalIconButton(
            onClick = onSave,
            enabled = canSave,
            modifier = Modifier.size(52.dp),
        ) {
            Icon(
                imageVector = Icons.Outlined.Save,
                contentDescription = if (state.phase == TranscriptionPhase.SAVING) "Saving…" else "Save",
                modifier = Modifier.size(24.dp),
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AdvancedSheetContent(
    state: TranscriptionUiState,
    onHistory: () -> Unit,
    onViewOriginal: () -> Unit,
    onViewSessionDetails: () -> Unit,
    onRetranscribeSession: () -> Unit,
    onImport: () -> Unit,
    onClear: () -> Unit,
    onSelectEngine: (AsrEngineChoice) -> Unit,
    onHotwordsChanged: (Boolean) -> Unit,
    onSelectFont: (AppFont) -> Unit,
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
        AdvancedRow(
            icon = Icons.Outlined.History,
            title = "History",
            description = "Browse and reopen previous sessions (${state.historySummaries.size} saved)",
            enabled = ready,
            onClick = onHistory,
        )
        if (state.isEdited && state.originalTranscript.isNotBlank()) {
            AdvancedRow(
                icon = Icons.Outlined.Compare,
                title = "View original transcription",
                description = "Compare user edits with original raw ASR output",
                enabled = ready,
                onClick = onViewOriginal,
            )
        }
        if (state.activeSessionId != null) {
            AdvancedRow(
                icon = Icons.Outlined.Info,
                title = "Session details",
                description = "View metadata, word count, and run history",
                enabled = ready,
                onClick = onViewSessionDetails,
            )
        }
        AdvancedRow(Icons.Outlined.FolderOpen, "Import WAV", "Open a local WAV file", enabled = ready, onClick = onImport)
        AdvancedRow(
            Icons.Outlined.DeleteOutline,
            "Clear current screen",
            "Unload active session (remains in History)",
            destructive = true,
            enabled = ready && (state.hasAudio || state.transcript.isNotEmpty()),
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

        AdvancedSectionLabel("TYPOGRAPHY & FONT")
        Text("App Font Family", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 6.dp))
        Text(
            "Select the typeface used across the interface and transcript",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        FlowRow(
            modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            AppFont.entries.forEach { font ->
                FilterChip(
                    selected = state.appFont == font,
                    onClick = { onSelectFont(font) },
                    label = { Text(font.displayName) },
                )
            }
        }

        AdvancedSectionLabel("TESTING")
        AdvancedRow(
            Icons.Outlined.Autorenew,
            "Retranscribe session",
            "Run alternate engine on stored audio",
            enabled = ready && state.hasAudio,
            onClick = onRetranscribeSession,
        )
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
                Text("Developed by:\nPradeep\n\nSenior Principal Engineer and Architect at Dell Technologies")
                Text("Focus areas:\nAndroid & Linux Platform Engineering\nEmbedded Systems\nSystem Architecture")
                Text("Privacy:\nAudio and transcripts are processed locally on the device. The application does not require Internet access.")
                Text("Version 0.4\nMoonshine Base and Zipformer via sherpa-onnx")
            }
        },
    )
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
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(12.dp)
                    .clip(CircleShape)
                    .background(accent),
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 12.dp),
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                )
                Text(
                    text = if (isRecording) "16 kHz active" else subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
            if (isRecording) {
                AudioLevelMeter(waveform.lastOrNull() ?: 0f)
                Text(
                    text = RecordingMetrics.formatDuration(elapsedMs),
                    modifier = Modifier
                        .padding(start = 10.dp)
                        .wrapContentWidth(),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
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
            .padding(start = 6.dp)
            .size(width = 72.dp, height = 24.dp),
    ) {
        val barCount = 10
        val gap = 2.dp.toPx()
        val barWidth = 5.dp.toPx()
        val activeBars = (level.coerceIn(0f, 1f) * barCount).toInt().coerceIn(1, barCount)
        repeat(barCount) { index ->
            val color = when {
                index >= 9 -> red
                index >= 8 -> orange
                index >= 6 -> yellow
                else -> green
            }
            val height = (6.dp.toPx() + (index + 1) * 1.5.dp.toPx())
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
    isEdited: Boolean,
    isEditing: Boolean,
    canEdit: Boolean,
    onStartEdit: () -> Unit,
    onCancelEdit: () -> Unit,
    onSaveEdit: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var localText by remember(transcript, isEditing) { mutableStateOf(transcript) }
    val displayWordCount = remember(localText, isEditing, transcript) {
        val textToCount = if (isEditing) localText else transcript
        countWords(textToCount)
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
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = stringResource(R.string.transcript),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
                if (isEdited && !isEditing) {
                    Surface(
                        color = MaterialTheme.colorScheme.tertiaryContainer,
                        shape = RoundedCornerShape(50),
                    ) {
                        Text(
                            text = "Edited",
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onTertiaryContainer,
                        )
                    }
                }
                Surface(
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                    shape = RoundedCornerShape(50),
                ) {
                    Text(
                        text = pluralStringResource(R.plurals.word_count, displayWordCount, displayWordCount),
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                if (transcript.isNotEmpty() && canEdit) {
                    if (isEditing) {
                        TextButton(
                            onClick = onCancelEdit,
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                        ) {
                            Text("Cancel", style = MaterialTheme.typography.labelMedium)
                        }
                        FilledTonalButton(
                            onClick = { onSaveEdit(localText) },
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                        ) {
                            Text("Done", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                        }
                    } else {
                        OutlinedButton(
                            onClick = onStartEdit,
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Edit,
                                contentDescription = "Edit transcript",
                                modifier = Modifier.size(16.dp),
                            )
                            Spacer(Modifier.width(4.dp))
                            Text("Edit", style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 16.dp),
            ) {
                if (isEditing) {
                    BasicTextField(
                        value = localText,
                        onValueChange = { localText = it },
                        modifier = Modifier.fillMaxSize(),
                        textStyle = MaterialTheme.typography.bodyLarge.copy(
                            color = MaterialTheme.colorScheme.onSurface,
                            lineHeight = 27.sp,
                        ),
                        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    )
                } else if (transcript.isEmpty()) {
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HistorySheet(
    summaries: List<SessionSummary>,
    searchQuery: String,
    onSearchChange: (String) -> Unit,
    onOpenSession: (String) -> Unit,
    onRenameSession: (SessionSummary) -> Unit,
    onDeleteSession: (SessionSummary) -> Unit,
    onViewDetails: (SessionSummary) -> Unit,
    onBack: () -> Unit,
    onDismiss: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .fillMaxHeight(0.88f)
            .padding(horizontal = 20.dp, vertical = 8.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back to Advanced")
            }
            Text(
                "History",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f).padding(start = 8.dp),
            )
            IconButton(onClick = onDismiss) {
                Icon(Icons.Outlined.Clear, contentDescription = "Close history")
            }
        }

        OutlinedTextField(
            value = searchQuery,
            onValueChange = onSearchChange,
            placeholder = { Text("Search title or transcript…") },
            leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
            trailingIcon = {
                if (searchQuery.isNotEmpty()) {
                    IconButton(onClick = { onSearchChange("") }) {
                        Icon(Icons.Outlined.Clear, contentDescription = "Clear search")
                    }
                }
            },
            singleLine = true,
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
        )

        if (summaries.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxWidth().weight(1f),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = if (searchQuery.isNotEmpty()) {
                        "No sessions match \"$searchQuery\""
                    } else {
                        "No saved sessions in history yet.\nRecord or import audio to create sessions."
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
        } else {
            val grouped = remember(summaries) {
                summaries.groupBy { formatDateGroup(it.createdAt) }
            }

            LazyColumn(
                modifier = Modifier.fillMaxWidth().weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                grouped.forEach { (dateHeader, itemsInGroup) ->
                    item(key = "header_$dateHeader") {
                        Text(
                            text = dateHeader,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(top = 10.dp, bottom = 4.dp),
                        )
                    }
                    items(itemsInGroup, key = { it.id }) { summary ->
                        HistorySessionItem(
                            summary = summary,
                            onClick = { onOpenSession(summary.id) },
                            onRename = { onRenameSession(summary) },
                            onDelete = { onDeleteSession(summary) },
                            onDetails = { onViewDetails(summary) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun HistorySessionItem(
    summary: SessionSummary,
    onClick: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
    onDetails: () -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    val timeFormat = remember { SimpleDateFormat("h:mm a", Locale.getDefault()) }
    val timeString = remember(summary.createdAt) { timeFormat.format(Date(summary.createdAt)) }

    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f)),
    ) {
        Row(
            modifier = Modifier.padding(start = 16.dp, end = 4.dp, top = 12.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = summary.title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(
                        text = "${RecordingMetrics.formatDuration((summary.audioDurationSeconds * 1000).toLong())} • ${summary.wordCount} words",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (summary.isEdited) {
                        Surface(
                            color = MaterialTheme.colorScheme.tertiaryContainer,
                            shape = RoundedCornerShape(50),
                        ) {
                            Text(
                                text = "Edited",
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onTertiaryContainer,
                            )
                        }
                    }
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(
                        text = if (summary.hotwordsEnabled && summary.engine.contains("Zipformer", ignoreCase = true)) {
                            "${summary.engine} • Hotwords"
                        } else {
                            summary.engine
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                    )
                    Text(
                        text = "•",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                    )
                    Text(
                        text = timeString,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                    )
                }
            }
            Box {
                IconButton(onClick = { menuOpen = true }) {
                    Icon(
                        imageVector = Icons.Outlined.MoreVert,
                        contentDescription = "Session options",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    DropdownMenuItem(
                        text = { Text("Open") },
                        onClick = {
                            menuOpen = false
                            onClick()
                        },
                    )
                    DropdownMenuItem(
                        text = { Text("Rename") },
                        onClick = {
                            menuOpen = false
                            onRename()
                        },
                    )
                    DropdownMenuItem(
                        text = { Text("Details") },
                        onClick = {
                            menuOpen = false
                            onDetails()
                        },
                    )
                    HorizontalDivider()
                    DropdownMenuItem(
                        text = { Text("Delete", color = MaterialTheme.colorScheme.error) },
                        onClick = {
                            menuOpen = false
                            onDelete()
                        },
                    )
                }
            }
        }
    }
}

private fun formatDateGroup(timestamp: Long): String {
    val calendar = Calendar.getInstance()
    val todayYear = calendar.get(Calendar.YEAR)
    val todayDay = calendar.get(Calendar.DAY_OF_YEAR)

    calendar.timeInMillis = timestamp
    val sessionYear = calendar.get(Calendar.YEAR)
    val sessionDay = calendar.get(Calendar.DAY_OF_YEAR)

    return when {
        todayYear == sessionYear && todayDay == sessionDay -> "Today"
        todayYear == sessionYear && todayDay - sessionDay == 1 -> "Yesterday"
        else -> SimpleDateFormat("MMM d, yyyy", Locale.getDefault()).format(Date(timestamp))
    }
}

@Composable
private fun RenameSessionDialog(
    initialTitle: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var title by remember { mutableStateOf(initialTitle) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Rename Session") },
        text = {
            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                label = { Text("Session Title") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (title.isNotBlank()) {
                        onConfirm(title.trim())
                    }
                },
                enabled = title.isNotBlank(),
            ) { Text("Rename") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

@Composable
private fun DeleteSessionDialog(
    summary: SessionSummary,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Delete this session?") },
        text = {
            Text(
                "This will remove \"${summary.title}\" and its internal audio from app history.\n\n" +
                "Any files you previously exported to Music will NOT be deleted."
            )
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text("Delete from History", color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

@Composable
private fun OriginalTranscriptDialog(
    originalTranscript: String,
    onRestore: () -> Unit,
    onDismiss: () -> Unit,
) {
    var confirmRestoreOpen by remember { mutableStateOf(false) }

    if (confirmRestoreOpen) {
        AlertDialog(
            onDismissRequest = { confirmRestoreOpen = false },
            title = { Text("Restore original transcript?") },
            text = { Text("This will discard all your manual edits and revert to the raw speech recognition text.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmRestoreOpen = false
                        onRestore()
                        onDismiss()
                    },
                ) { Text("Restore", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { confirmRestoreOpen = false }) { Text("Cancel") }
            },
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Original ASR Output") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 360.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                Text(
                    text = "Raw recognition text before your edits:",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                ) {
                    SelectionContainer {
                        Text(
                            text = originalTranscript,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(12.dp),
                            lineHeight = 22.sp,
                        )
                    }
                }
                Text(
                    text = "${countWords(originalTranscript)} words",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { confirmRestoreOpen = true }) {
                Text("Restore Original", color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        },
    )
}

@Composable
private fun RetranscribeDialog(
    currentEngine: AsrEngineChoice,
    hotwordsEnabled: Boolean,
    isEdited: Boolean,
    onRetranscribe: (AsrEngineChoice, Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    var selectedEngine by remember { mutableStateOf(currentEngine) }
    var hotwords by remember { mutableStateOf(hotwordsEnabled) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Retranscribe Session") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    "Re-run transcription on the stored audio using an offline model:",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AsrEngineChoice.entries.forEach { choice ->
                        FilterChip(
                            selected = selectedEngine == choice,
                            onClick = { selectedEngine = choice },
                            label = { Text(if (choice == AsrEngineChoice.ZIPFORMER) "Zipformer" else "Moonshine") },
                        )
                    }
                }
                if (selectedEngine == AsrEngineChoice.ZIPFORMER) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            "Technical hotwords",
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f),
                        )
                        Switch(checked = hotwords, onCheckedChange = { hotwords = it })
                    }
                }
                if (isEdited) {
                    Surface(
                        color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                    ) {
                        Text(
                            text = "This session contains manual edits. Retranscribing will create a new recognition result. Choose whether to keep your edits or replace them.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.padding(10.dp),
                        )
                    }
                }
            }
        },
        confirmButton = {
            if (isEdited) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    OutlinedButton(
                        onClick = {
                            onRetranscribe(selectedEngine, false)
                            onDismiss()
                        },
                    ) {
                        Text("Keep Edits", style = MaterialTheme.typography.labelMedium)
                    }
                    Button(
                        onClick = {
                            onRetranscribe(selectedEngine, true)
                            onDismiss()
                        },
                    ) {
                        Text("Replace", style = MaterialTheme.typography.labelMedium)
                    }
                }
            } else {
                Button(
                    onClick = {
                        onRetranscribe(selectedEngine, true)
                        onDismiss()
                    },
                ) {
                    Text("Retranscribe")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

@Composable
private fun SessionDetailsDialog(
    session: TranscriptionSession,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(session.title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 420.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                val dateFormat = SimpleDateFormat("MMM d, yyyy 'at' h:mm a", Locale.getDefault())
                SessionDetailRow("Created", dateFormat.format(Date(session.createdAt)))
                SessionDetailRow("Source", if (session.sourceType == SessionSource.IMPORTED) "Imported WAV" else "Microphone recording")
                SessionDetailRow("Audio Duration", "${String.format(Locale.US, "%.2f", session.audioDurationSeconds)} s (${RecordingMetrics.formatDuration((session.audioDurationSeconds * 1000).toLong())})")
                SessionDetailRow("Word Count", "${session.wordCount} words")
                SessionDetailRow("Engine", session.engine)
                if (session.hotwordsEnabled) {
                    SessionDetailRow("Technical Hotwords", "Enabled")
                }
                SessionDetailRow("Inference Time", "${String.format(Locale.US, "%.2f", session.inferenceDurationSeconds)} s")
                SessionDetailRow("RTF", String.format(Locale.US, "%.4f", session.rtf))
                SessionDetailRow("Manual Edits", if (session.isEdited) "Yes (original preserved)" else "None (raw ASR)")

                if (session.runs.size > 1) {
                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                    Text(
                        "ASR Run History (${session.runs.size} runs):",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                    )
                    session.runs.forEachIndexed { idx, run ->
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                        ) {
                            Column(modifier = Modifier.padding(8.dp)) {
                                Text(
                                    "Run #${idx + 1}: ${run.engine} ${if (run.hotwordsEnabled) "• Hotwords" else ""}",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.SemiBold,
                                )
                                Text(
                                    "RTF: ${String.format(Locale.US, "%.4f", run.rtf)} • ${run.wordCount} words • ${String.format(Locale.US, "%.2f", run.inferenceDurationSeconds)} s",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        },
    )
}

@Composable
private fun SessionDetailRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Medium,
        )
    }
}
