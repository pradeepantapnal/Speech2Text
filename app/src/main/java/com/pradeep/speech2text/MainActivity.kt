package com.pradeep.speech2text

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import com.pradeep.speech2text.ui.theme.Speech2TextTheme

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
        super.onStop()
    }
}

@Composable
private fun TranscriptionScreen(
    viewModel: TranscriptionViewModel,
    hasMicrophonePermission: () -> Boolean,
) {
    val state by viewModel.uiState.collectAsState()
    var permissionDenied by remember { mutableStateOf(false) }
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
            )

            state.metrics?.let { metrics ->
                BenchmarkPanel(metrics)
            }

            TranscriptPanel(
                transcript = state.transcript,
                modifier = Modifier.weight(1f),
            )

            ActionPanel(
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
                onRetranscribe = viewModel::retranscribe,
                onSave = {
                    if (viewModel.hasMusicFolderAccess()) {
                        viewModel.saveTranscript()
                    } else {
                        musicFolderLauncher.launch(INITIAL_MUSIC_FOLDER_URI)
                    }
                },
                onClear = viewModel::clearTranscript,
            )
        }
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
) {
    val isError = phase == TranscriptionPhase.ERROR || permissionDenied
    val accent = when {
        isError -> MaterialTheme.colorScheme.error
        phase == TranscriptionPhase.RECORDING -> MaterialTheme.colorScheme.error
        phase == TranscriptionPhase.TRANSCRIBING || phase == TranscriptionPhase.SAVING ->
            MaterialTheme.colorScheme.tertiary
        else -> MaterialTheme.colorScheme.primary
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = accent.copy(alpha = 0.11f),
        shape = RoundedCornerShape(20.dp),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 15.dp),
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
                    .padding(start = 14.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = status,
                    style = MaterialTheme.typography.labelLarge,
                    color = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = if (phase == TranscriptionPhase.RECORDING) {
                        stringResource(R.string.microphone_active)
                    } else {
                        stringResource(R.string.offline_model)
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
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
                    Text(
                        text = stringResource(R.string.transcript_empty),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
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
                onClick = onRecord,
                enabled = state.phase == TranscriptionPhase.READY,
                modifier = Modifier
                    .weight(1f)
                    .height(52.dp),
                shape = RoundedCornerShape(16.dp),
                contentPadding = PaddingValues(horizontal = 6.dp),
            ) {
                Text(stringResource(R.string.record))
            }
            FilledTonalButton(
                onClick = onStop,
                enabled = state.phase == TranscriptionPhase.RECORDING,
                modifier = Modifier
                    .weight(1f)
                    .height(52.dp),
                shape = RoundedCornerShape(16.dp),
                contentPadding = PaddingValues(horizontal = 6.dp),
            ) {
                Text(stringResource(R.string.stop))
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
                Text(stringResource(R.string.import_wav))
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
                Text(stringResource(R.string.retest))
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
                Text(
                    if (state.phase == TranscriptionPhase.SAVING) {
                        stringResource(R.string.saving)
                    } else {
                        stringResource(R.string.save)
                    },
                )
            }
            OutlinedButton(
                onClick = onClear,
                enabled = (state.transcript.isNotEmpty() || state.hasAudio) && !isBusy,
                modifier = Modifier
                    .weight(1f)
                    .height(52.dp),
                shape = RoundedCornerShape(16.dp),
                contentPadding = PaddingValues(horizontal = 6.dp),
            ) {
                Text(stringResource(R.string.clear))
            }
        }
    }
}
