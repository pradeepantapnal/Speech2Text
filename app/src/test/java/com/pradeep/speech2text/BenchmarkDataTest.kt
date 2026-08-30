package com.pradeep.speech2text

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BenchmarkDataTest {
    @Test
    fun rtfUsesInferenceSecondsDividedByAudioSeconds() {
        assertEquals(0.153333, calculateRtf(18_400.0, 120.0), 0.000001)
    }

    @Test
    fun rtfIsZeroForEmptyAudio() {
        assertEquals(0.0, calculateRtf(100.0, 0.0), 0.0)
    }

    @Test
    fun wordCountHandlesWhitespaceAndEmptyText() {
        assertEquals(4, countWords("  one  two\nthree\tfour "))
        assertEquals(0, countWords(" \n\t "))
    }

    @Test
    fun metadataIsUtf8SafeValidJsonShape() {
        val json = BenchmarkMetadata(
            model = "Moonshine \"Base\"",
            backend = "sherpa-onnx / CPU",
            audioDurationSeconds = 120.0,
            inferenceDurationSeconds = 18.4,
            rtf = 0.153333,
            wordCount = 286,
            sampleRate = 16_000,
            channels = 1,
            device = "Test\\Device",
            deviceAbi = "arm64-v8a",
            androidVersion = "16",
            appVersion = "0.2",
            source = "imported WAV",
        ).toJson()

        assertTrue(json.startsWith("{\n"))
        assertTrue(json.endsWith("}"))
        assertTrue(json.contains("\"model\": \"Moonshine \\\"Base\\\"\""))
        assertTrue(json.contains("\"audio_duration_seconds\": 120.000000"))
        assertTrue(json.contains("\"rtf\": 0.153333"))
        assertTrue(json.contains("\"device\": \"Test\\\\Device\""))
        assertTrue(json.toByteArray(Charsets.UTF_8).isNotEmpty())
    }
}
