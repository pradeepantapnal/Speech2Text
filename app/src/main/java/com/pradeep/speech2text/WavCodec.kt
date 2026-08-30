package com.pradeep.speech2text

import java.io.EOFException
import java.io.InputStream
import java.io.OutputStream
import kotlin.math.max
import kotlin.math.roundToInt

data class PcmAudio(
    val samples: ShortArray,
    val sampleRate: Int,
    val channels: Int,
) {
    init {
        require(sampleRate > 0) { "Sample rate must be positive" }
        require(channels > 0) { "Channel count must be positive" }
        require(samples.size % channels == 0) { "PCM samples are not aligned to channel frames" }
    }

    val frameCount: Int
        get() = samples.size / channels

    val durationSeconds: Double
        get() = frameCount.toDouble() / sampleRate

    fun toModelFloatArray(): FloatArray {
        require(sampleRate == WavCodec.MODEL_SAMPLE_RATE && channels == 1) {
            "Moonshine input must be 16 kHz mono PCM"
        }
        return FloatArray(samples.size) { index -> samples[index] / 32768.0f }
    }
}

class WavFormatException(message: String) : IllegalArgumentException(message)

object WavCodec {
    const val MODEL_SAMPLE_RATE = 16_000
    private const val PCM_FORMAT = 1
    private const val PCM_BITS = 16
    private const val MAX_FORMAT_CHUNK_BYTES = 1_048_576
    private const val MAX_DATA_CHUNK_BYTES = 134_217_728

    /** Reads RIFF/WAVE PCM16, including files with harmless extra chunks. */
    fun read(input: InputStream): PcmAudio {
        val stream = input.buffered()
        if (readFourCc(stream) != "RIFF") throw WavFormatException("Not a RIFF WAV file")
        readUInt32Le(stream) // RIFF size; individual chunks are validated below.
        if (readFourCc(stream) != "WAVE") throw WavFormatException("RIFF file is not WAVE audio")

        var format: WaveFormat? = null
        var pcmBytes: ByteArray? = null

        while (format == null || pcmBytes == null) {
            val chunkId = readFourCcOrNull(stream) ?: break
            val chunkSizeLong = readUInt32Le(stream)
            if (chunkSizeLong > Int.MAX_VALUE) {
                throw WavFormatException("WAV chunk is too large")
            }
            val chunkSize = chunkSizeLong.toInt()

            when (chunkId) {
                "fmt " -> {
                    if (chunkSize !in 16..MAX_FORMAT_CHUNK_BYTES) {
                        throw WavFormatException("Invalid WAV format chunk size: $chunkSize")
                    }
                    val bytes = readExact(stream, chunkSize)
                    format = WaveFormat(
                        encoding = uint16Le(bytes, 0),
                        channels = uint16Le(bytes, 2),
                        sampleRate = uint32Le(bytes, 4),
                        blockAlign = uint16Le(bytes, 12),
                        bitsPerSample = uint16Le(bytes, 14),
                    )
                }

                "data" -> {
                    if (chunkSize > MAX_DATA_CHUNK_BYTES) {
                        throw WavFormatException("WAV audio is larger than the 128 MiB import limit")
                    }
                    pcmBytes = readExact(stream, chunkSize)
                }

                else -> skipExact(stream, chunkSize.toLong())
            }

            if (chunkSize and 1 == 1) skipExact(stream, 1)
        }

        val waveFormat = format ?: throw WavFormatException("WAV format chunk is missing")
        val data = pcmBytes ?: throw WavFormatException("WAV data chunk is missing")
        validateFormat(waveFormat, data.size)

        val samples = ShortArray(data.size / Short.SIZE_BYTES)
        samples.indices.forEach { index ->
            val offset = index * Short.SIZE_BYTES
            samples[index] = ((data[offset].toInt() and 0xff) or (data[offset + 1].toInt() shl 8)).toShort()
        }
        return PcmAudio(samples, waveFormat.sampleRate, waveFormat.channels)
    }

    /** Converts supported mono/stereo PCM into Moonshine's 16 kHz mono input. */
    fun normalizeForMoonshine(source: PcmAudio): PcmAudio {
        require(source.channels in 1..2) { "Only mono or stereo PCM WAV is supported" }
        require(source.sampleRate in 8_000..192_000) {
            "Sample rate ${source.sampleRate} Hz is unsupported; expected 8–192 kHz"
        }

        val mono = if (source.channels == 1) {
            source.samples.copyOf()
        } else {
            ShortArray(source.frameCount) { frame ->
                val left = source.samples[frame * 2].toInt()
                val right = source.samples[frame * 2 + 1].toInt()
                ((left + right) / 2).toShort()
            }
        }

        if (source.sampleRate == MODEL_SAMPLE_RATE) {
            return PcmAudio(mono, MODEL_SAMPLE_RATE, 1)
        }
        if (mono.isEmpty()) return PcmAudio(mono, MODEL_SAMPLE_RATE, 1)

        val outputFrames = max(
            1,
            (mono.size.toDouble() * MODEL_SAMPLE_RATE / source.sampleRate).roundToInt(),
        )
        val resampled = ShortArray(outputFrames) { outputIndex ->
            val sourcePosition = outputIndex.toDouble() * source.sampleRate / MODEL_SAMPLE_RATE
            val lowerIndex = sourcePosition.toInt().coerceIn(0, mono.lastIndex)
            val upperIndex = (lowerIndex + 1).coerceAtMost(mono.lastIndex)
            val fraction = sourcePosition - lowerIndex
            val interpolated = mono[lowerIndex] + (mono[upperIndex] - mono[lowerIndex]) * fraction
            interpolated.roundToInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
        }
        return PcmAudio(resampled, MODEL_SAMPLE_RATE, 1)
    }

    /** Writes canonical little-endian RIFF/WAVE PCM16 without compression. */
    fun write(audio: PcmAudio, output: OutputStream) {
        require(audio.channels in 1..2) { "Only mono or stereo PCM WAV can be written" }
        val dataSize = audio.samples.size * Short.SIZE_BYTES
        val blockAlign = audio.channels * Short.SIZE_BYTES
        val byteRate = audio.sampleRate * blockAlign

        output.write("RIFF".toByteArray(Charsets.US_ASCII))
        writeUInt32Le(output, 36L + dataSize)
        output.write("WAVE".toByteArray(Charsets.US_ASCII))
        output.write("fmt ".toByteArray(Charsets.US_ASCII))
        writeUInt32Le(output, 16)
        writeUInt16Le(output, PCM_FORMAT)
        writeUInt16Le(output, audio.channels)
        writeUInt32Le(output, audio.sampleRate.toLong())
        writeUInt32Le(output, byteRate.toLong())
        writeUInt16Le(output, blockAlign)
        writeUInt16Le(output, PCM_BITS)
        output.write("data".toByteArray(Charsets.US_ASCII))
        writeUInt32Le(output, dataSize.toLong())
        audio.samples.forEach { sample -> writeUInt16Le(output, sample.toInt() and 0xffff) }
    }

    private fun validateFormat(format: WaveFormat, dataSize: Int) {
        if (format.encoding != PCM_FORMAT) {
            throw WavFormatException("Unsupported WAV encoding ${format.encoding}; only PCM is supported")
        }
        if (format.bitsPerSample != PCM_BITS) {
            throw WavFormatException("Unsupported ${format.bitsPerSample}-bit WAV; only 16-bit PCM is supported")
        }
        if (format.channels !in 1..2) {
            throw WavFormatException("Unsupported channel count ${format.channels}; use mono or stereo")
        }
        if (format.sampleRate !in 8_000..192_000) {
            throw WavFormatException("Unsupported sample rate ${format.sampleRate} Hz; expected 8–192 kHz")
        }
        val expectedBlockAlign = format.channels * Short.SIZE_BYTES
        if (format.blockAlign != expectedBlockAlign || dataSize % expectedBlockAlign != 0) {
            throw WavFormatException("PCM data is not aligned to 16-bit channel frames")
        }
    }

    private fun readFourCc(input: InputStream): String = String(readExact(input, 4), Charsets.US_ASCII)

    private fun readFourCcOrNull(input: InputStream): String? {
        val first = input.read()
        if (first == -1) return null
        val remaining = readExact(input, 3)
        return String(byteArrayOf(first.toByte(), *remaining), Charsets.US_ASCII)
    }

    private fun readExact(input: InputStream, byteCount: Int): ByteArray {
        val result = ByteArray(byteCount)
        var offset = 0
        while (offset < byteCount) {
            val count = input.read(result, offset, byteCount - offset)
            if (count < 0) throw WavFormatException("Unexpected end of WAV file")
            offset += count
        }
        return result
    }

    private fun skipExact(input: InputStream, byteCount: Long) {
        var remaining = byteCount
        while (remaining > 0) {
            val skipped = input.skip(remaining)
            if (skipped > 0) {
                remaining -= skipped
            } else if (input.read() == -1) {
                throw EOFException("Unexpected end of WAV file")
            } else {
                remaining--
            }
        }
    }

    private fun readUInt32Le(input: InputStream): Long {
        val bytes = readExact(input, 4)
        return uint32LeLong(bytes, 0)
    }

    private fun uint16Le(bytes: ByteArray, offset: Int): Int =
        (bytes[offset].toInt() and 0xff) or ((bytes[offset + 1].toInt() and 0xff) shl 8)

    private fun uint32Le(bytes: ByteArray, offset: Int): Int {
        val value = uint32LeLong(bytes, offset)
        if (value > Int.MAX_VALUE) throw WavFormatException("WAV integer is too large")
        return value.toInt()
    }

    private fun uint32LeLong(bytes: ByteArray, offset: Int): Long =
        (bytes[offset].toLong() and 0xff) or
            ((bytes[offset + 1].toLong() and 0xff) shl 8) or
            ((bytes[offset + 2].toLong() and 0xff) shl 16) or
            ((bytes[offset + 3].toLong() and 0xff) shl 24)

    private fun writeUInt16Le(output: OutputStream, value: Int) {
        output.write(value and 0xff)
        output.write((value ushr 8) and 0xff)
    }

    private fun writeUInt32Le(output: OutputStream, value: Long) {
        require(value in 0..0xffff_ffffL) { "WAV size exceeds RIFF limits" }
        output.write((value and 0xff).toInt())
        output.write(((value ushr 8) and 0xff).toInt())
        output.write(((value ushr 16) and 0xff).toInt())
        output.write(((value ushr 24) and 0xff).toInt())
    }

    private data class WaveFormat(
        val encoding: Int,
        val channels: Int,
        val sampleRate: Int,
        val blockAlign: Int,
        val bitsPerSample: Int,
    )
}
