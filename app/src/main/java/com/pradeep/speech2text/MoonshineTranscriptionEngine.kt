package com.pradeep.speech2text

import android.app.Application
import android.os.SystemClock
import com.k2fsa.sherpa.onnx.FeatureConfig
import com.k2fsa.sherpa.onnx.OfflineModelConfig
import com.k2fsa.sherpa.onnx.OfflineMoonshineModelConfig
import com.k2fsa.sherpa.onnx.OfflineRecognizer
import com.k2fsa.sherpa.onnx.OfflineRecognizerConfig

class MoonshineTranscriptionEngine(
    application: Application,
    private val onChunkProgress: (completed: Int, total: Int) -> Unit = { _, _ -> },
) : TranscriptionEngine {
    private val recognizer = createRecognizer(application)

    override suspend fun transcribe(audio: FloatArray): TranscriptionResult {
        require(audio.isNotEmpty()) { "Audio is empty" }

        val (text, elapsedNanos) = transcribeInSafeChunks(audio)
        return TranscriptionResult(
            text = text,
            inferenceDurationMs = elapsedNanos / 1_000_000.0,
            modelName = MODEL_NAME,
            backendInfo = BACKEND_INFO,
        )
    }

    override fun close() {
        recognizer.release()
    }

    private fun transcribeInSafeChunks(samples: FloatArray): Pair<String, Long> {
        // The bundled Sherpa Moonshine v2 decoder is reliable below ten
        // seconds, so split longer recordings while retaining one result and
        // one benchmark measurement for the complete input.
        val partCount = (samples.size + MAX_CHUNK_SAMPLES - 1) / MAX_CHUNK_SAMPLES
        val parts = ArrayList<String>(partCount)
        var inferenceNanos = 0L

        repeat(partCount) { partIndex ->
            onChunkProgress(partIndex + 1, partCount)
            val start = partIndex * MAX_CHUNK_SAMPLES
            val end = minOf(start + MAX_CHUNK_SAMPLES, samples.size)
            val chunk = samples.copyOfRange(start, end)
            // Monotonic time excludes wall-clock changes and wraps only around
            // the actual Sherpa stream/decode work, not UI or file I/O.
            val startedNanos = SystemClock.elapsedRealtimeNanos()
            val stream = recognizer.createStream()
            val text = try {
                stream.acceptWaveform(chunk, SAMPLE_RATE)
                recognizer.decode(stream)
                recognizer.getResult(stream).text.trim()
            } finally {
                stream.release()
                inferenceNanos += SystemClock.elapsedRealtimeNanos() - startedNanos
            }
            if (text.isNotEmpty()) parts += text
        }

        return parts.joinToString(separator = " ") to inferenceNanos
    }

    private fun createRecognizer(application: Application): OfflineRecognizer {
        val modelConfig = OfflineModelConfig(
            moonshine = OfflineMoonshineModelConfig(
                encoder = "$MODEL_DIR/encoder_model.ort",
                mergedDecoder = "$MODEL_DIR/decoder_model_merged.ort",
            ),
            tokens = "$MODEL_DIR/tokens.txt",
            numThreads = Runtime.getRuntime().availableProcessors().coerceIn(1, 4),
            debug = false,
            provider = "cpu",
            modelType = "moonshine",
        )
        val config = OfflineRecognizerConfig(
            featConfig = FeatureConfig(
                sampleRate = SAMPLE_RATE,
                featureDim = 80,
                dither = 0.0f,
            ),
            modelConfig = modelConfig,
            decodingMethod = "greedy_search",
        )
        return OfflineRecognizer(application.assets, config)
    }

    private companion object {
        const val SAMPLE_RATE = WavCodec.MODEL_SAMPLE_RATE
        // The current Sherpa-exported Moonshine v2 decoder fails at >=10 seconds.
        // Eight-second pieces preserve the known-good transcription path.
        const val MAX_CHUNK_SAMPLES = SAMPLE_RATE * 8
        const val MODEL_DIR = "sherpa-onnx-moonshine-base-en-quantized-2026-02-27"
        const val MODEL_NAME = "Moonshine Base"
        const val BACKEND_INFO = "sherpa-onnx 1.13.4 / ONNX Runtime CPU"
    }
}
