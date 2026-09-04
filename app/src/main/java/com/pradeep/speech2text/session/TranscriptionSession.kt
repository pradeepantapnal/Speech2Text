package com.pradeep.speech2text.session

/**
 * Indicates where the audio originated.
 */
enum class SessionSource {
    RECORDED,
    IMPORTED,
}

/**
 * Represents a single ASR inference execution on the session's audio.
 * Allows preserving raw recognition runs over time (e.g. comparing Moonshine and Zipformer).
 */
data class TranscriptionRun(
    val id: String,
    val engine: String,
    val hotwordsEnabled: Boolean,
    val transcript: String,
    val inferenceDurationSeconds: Double,
    val rtf: Double,
    val wordCount: Int,
    val timestamp: Long,
)

/**
 * Core local session entity in V0.5.
 *
 * Represents one piece of recorded/imported audio and its associated transcription work.
 * Preserves both original raw ASR output and user-edited text.
 */
data class TranscriptionSession(
    val id: String,
    val createdAt: Long,
    val modifiedAt: Long,
    val title: String,
    val sourceType: SessionSource,
    val audioRelativePath: String,
    val audioDurationSeconds: Double,
    val originalTranscript: String,
    val currentTranscript: String,
    val isEdited: Boolean,
    val engine: String,
    val inferenceDurationSeconds: Double,
    val rtf: Double,
    val wordCount: Int,
    val hotwordsEnabled: Boolean,
    val runs: List<TranscriptionRun> = emptyList(),
) {
    /** Converts the full session into a lightweight summary for fast History indexing. */
    fun toSummary(): SessionSummary = SessionSummary(
        id = id,
        createdAt = createdAt,
        modifiedAt = modifiedAt,
        title = title,
        sourceType = sourceType,
        audioDurationSeconds = audioDurationSeconds,
        wordCount = wordCount,
        engine = engine,
        hotwordsEnabled = hotwordsEnabled,
        isEdited = isEdited,
    )
}

/**
 * Lightweight metadata used in History list and index.
 * Does not include audio or large transcript contents to maintain high performance.
 */
data class SessionSummary(
    val id: String,
    val createdAt: Long,
    val modifiedAt: Long,
    val title: String,
    val sourceType: SessionSource,
    val audioDurationSeconds: Double,
    val wordCount: Int,
    val engine: String,
    val hotwordsEnabled: Boolean,
    val isEdited: Boolean,
)
