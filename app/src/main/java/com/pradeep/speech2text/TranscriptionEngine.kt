package com.pradeep.speech2text

/**
 * Small boundary around an offline ASR implementation.
 *
 * Keeping the engine contract limited to PCM samples and a result makes a
 * future engine (for example, Zipformer) replaceable without changing audio
 * capture, benchmarking, or export code.
 */
interface TranscriptionEngine : AutoCloseable {
    suspend fun transcribe(audio: FloatArray): TranscriptionResult
}

/** Result returned by an engine, including timing measured by that engine. */
data class TranscriptionResult(
    val text: String,
    val inferenceDurationMs: Double,
    val modelName: String,
    val backendInfo: String,
    val hotwordsEnabled: Boolean = false,
)
