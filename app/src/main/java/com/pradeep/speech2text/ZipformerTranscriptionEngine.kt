package com.pradeep.speech2text

import android.app.Application
import android.os.SystemClock
import com.k2fsa.sherpa.onnx.FeatureConfig
import com.k2fsa.sherpa.onnx.OfflineModelConfig
import com.k2fsa.sherpa.onnx.OfflineRecognizer
import com.k2fsa.sherpa.onnx.OfflineRecognizerConfig
import com.k2fsa.sherpa.onnx.OfflineTransducerModelConfig

/** Offline English Zipformer transducer with sherpa-onnx contextual biasing. */
class ZipformerTranscriptionEngine(
    application: Application,
    private val hotwordsEnabled: Boolean,
) : TranscriptionEngine {
    private val recognizer = OfflineRecognizer(application.assets, createConfig())

    override suspend fun transcribe(audio: FloatArray): TranscriptionResult {
        require(audio.isNotEmpty()) { "Audio is empty" }
        val startedNanos = SystemClock.elapsedRealtimeNanos()
        val stream = recognizer.createStream()
        val text = try {
            stream.acceptWaveform(audio, SAMPLE_RATE)
            recognizer.decode(stream)
            recognizer.getResult(stream).text.trim()
        } finally {
            stream.release()
        }
        return TranscriptionResult(
            text = text,
            inferenceDurationMs = (SystemClock.elapsedRealtimeNanos() - startedNanos) / 1_000_000.0,
            modelName = MODEL_NAME,
            backendInfo = BACKEND_INFO,
            hotwordsEnabled = hotwordsEnabled,
        )
    }

    override fun close() {
        recognizer.release()
    }

    private fun createConfig(): OfflineRecognizerConfig {
        val modelConfig = OfflineModelConfig(
            transducer = OfflineTransducerModelConfig(
                encoder = "$MODEL_DIR/encoder-epoch-99-avg-1.int8.onnx",
                decoder = "$MODEL_DIR/decoder-epoch-99-avg-1.onnx",
                joiner = "$MODEL_DIR/joiner-epoch-99-avg-1.onnx",
            ),
            tokens = "$MODEL_DIR/tokens.txt",
            numThreads = 2,
            debug = false,
            provider = "cpu",
            modelType = "zipformer",
            modelingUnit = "bpe",
        )
        return OfflineRecognizerConfig(
            featConfig = FeatureConfig(
                sampleRate = SAMPLE_RATE,
                featureDim = 80,
                dither = 0.0f,
            ),
            modelConfig = modelConfig,
            decodingMethod = "modified_beam_search",
            maxActivePaths = 4,
            hotwordsFile = if (hotwordsEnabled) HOTWORDS_FILE else "",
            hotwordsScore = HOTWORDS_SCORE,
        )
    }

    private companion object {
        const val SAMPLE_RATE = WavCodec.MODEL_SAMPLE_RATE
        const val MODEL_DIR = "sherpa-onnx-zipformer-small-en-2023-06-26"
        const val HOTWORDS_FILE = "$MODEL_DIR/technical_hotwords.txt"
        const val HOTWORDS_SCORE = 1.5f
        const val MODEL_NAME = "Zipformer Small English"
        const val BACKEND_INFO = "sherpa-onnx 1.13.4 / ONNX Runtime CPU"
    }
}
