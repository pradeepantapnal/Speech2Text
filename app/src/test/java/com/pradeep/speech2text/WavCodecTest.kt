package com.pradeep.speech2text

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

class WavCodecTest {
    @Test
    fun writesCanonicalPcm16HeaderAndReadsSamplesBack() {
        val expected = PcmAudio(
            samples = shortArrayOf(Short.MIN_VALUE, -1, 0, 1, Short.MAX_VALUE),
            sampleRate = 16_000,
            channels = 1,
        )
        val output = ByteArrayOutputStream()

        WavCodec.write(expected, output)
        val bytes = output.toByteArray()
        val actual = WavCodec.read(ByteArrayInputStream(bytes))

        assertEquals("RIFF", String(bytes, 0, 4, Charsets.US_ASCII))
        assertEquals("WAVE", String(bytes, 8, 4, Charsets.US_ASCII))
        assertEquals("fmt ", String(bytes, 12, 4, Charsets.US_ASCII))
        assertEquals("data", String(bytes, 36, 4, Charsets.US_ASCII))
        assertEquals(44 + expected.samples.size * 2, bytes.size)
        assertEquals(expected.sampleRate, actual.sampleRate)
        assertEquals(expected.channels, actual.channels)
        assertArrayEquals(expected.samples, actual.samples)
    }

    @Test
    fun normalizesStereoAndResamplesToMoonshineFormat() {
        val source = PcmAudio(
            samples = shortArrayOf(1_000, -1_000, 3_000, 1_000, -2_000, -2_000),
            sampleRate = 8_000,
            channels = 2,
        )

        val normalized = WavCodec.normalizeForMoonshine(source)

        assertEquals(16_000, normalized.sampleRate)
        assertEquals(1, normalized.channels)
        assertEquals(6, normalized.frameCount)
        assertEquals(0, normalized.samples[0].toInt())
        assertEquals(2_000, normalized.samples[2].toInt())
        assertEquals(-2_000, normalized.samples[4].toInt())
    }

    @Test
    fun rejectsUnsupportedBitDepthWithClearMessage() {
        val output = ByteArrayOutputStream()
        WavCodec.write(PcmAudio(shortArrayOf(1, 2), 16_000, 1), output)
        val malformed = output.toByteArray().also { bytes ->
            bytes[34] = 24
            bytes[35] = 0
        }

        val error = assertThrows(WavFormatException::class.java) {
            WavCodec.read(ByteArrayInputStream(malformed))
        }

        assertEquals("Unsupported 24-bit WAV; only 16-bit PCM is supported", error.message)
    }

    @Test
    fun rejectsTruncatedWav() {
        val error = assertThrows(WavFormatException::class.java) {
            WavCodec.read(ByteArrayInputStream("RIFF".toByteArray()))
        }

        assertEquals("Unexpected end of WAV file", error.message)
    }
}
