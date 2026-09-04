package com.pradeep.speech2text

import java.util.Locale

/** Measurements displayed beside the transcript and persisted in JSON. */
data class BenchmarkMetrics(
    val audioDurationSeconds: Double,
    val inferenceDurationSeconds: Double,
    val rtf: Double,
    val wordCount: Int,
    val modelName: String,
    val backendInfo: String,
    val deviceAbi: String,
)

/** Returns processing seconds divided by source-audio seconds. */
fun calculateRtf(
    inferenceDurationMs: Double,
    audioDurationSeconds: Double,
): Double = if (audioDurationSeconds > 0.0) {
    (inferenceDurationMs / 1_000.0) / audioDurationSeconds
} else {
    0.0
}

fun countWords(text: String): Int = text
    .trim()
    .takeIf(String::isNotEmpty)
    ?.split(Regex("\\s+"))
    ?.size
    ?: 0

/** Export schema kept dependency-free so benchmark files remain portable. */
data class BenchmarkMetadata(
    val model: String,
    val backend: String,
    val audioDurationSeconds: Double,
    val inferenceDurationSeconds: Double,
    val rtf: Double,
    val wordCount: Int,
    val sampleRate: Int,
    val channels: Int,
    val device: String,
    val deviceAbi: String,
    val androidVersion: String,
    val appVersion: String,
    val source: String,
    val isEdited: Boolean = false,
    val originalEngine: String? = null,
) {
    fun toJson(): String = buildString {
        appendLine("{")
        appendLine("  \"model\": \"${jsonEscape(model)}\",")
        appendLine("  \"backend\": \"${jsonEscape(backend)}\",")
        appendLine("  \"audio_duration_seconds\": ${jsonNumber(audioDurationSeconds)},")
        appendLine("  \"inference_duration_seconds\": ${jsonNumber(inferenceDurationSeconds)},")
        appendLine("  \"rtf\": ${jsonNumber(rtf)},")
        appendLine("  \"word_count\": $wordCount,")
        appendLine("  \"sample_rate\": $sampleRate,")
        appendLine("  \"channels\": $channels,")
        appendLine("  \"device\": \"${jsonEscape(device)}\",")
        appendLine("  \"device_abi\": \"${jsonEscape(deviceAbi)}\",")
        appendLine("  \"android_version\": \"${jsonEscape(androidVersion)}\",")
        appendLine("  \"app_version\": \"${jsonEscape(appVersion)}\",")
        appendLine("  \"source\": \"${jsonEscape(source)}\",")
        appendLine("  \"is_edited\": $isEdited" + if (originalEngine != null) "," else "")
        if (originalEngine != null) {
            appendLine("  \"original_engine\": \"${jsonEscape(originalEngine)}\"")
        }
        append("}")
    }
}

fun ComparisonResult.toJson(): String = buildString {
    appendLine("{")
    appendLine("  \"audio\": {")
    appendLine("    \"duration_seconds\": ${jsonNumber(audioDurationSeconds)},")
    appendLine("    \"sample_rate\": 16000")
    appendLine("  },")
    appendLine("  \"moonshine\": {")
    appendLine("    \"model\": \"${jsonEscape(moonshine.modelName)}\",")
    appendLine("    \"inference_seconds\": ${jsonNumber(moonshine.inferenceDurationSeconds)},")
    appendLine("    \"rtf\": ${jsonNumber(moonshine.rtf)},")
    appendLine("    \"word_count\": ${moonshine.wordCount},")
    appendLine("    \"transcript\": \"${jsonEscape(moonshineText)}\"")
    appendLine("  },")
    appendLine("  \"transducer\": {")
    appendLine("    \"model\": \"${jsonEscape(transducer.modelName)}\",")
    appendLine("    \"inference_seconds\": ${jsonNumber(transducer.inferenceDurationSeconds)},")
    appendLine("    \"rtf\": ${jsonNumber(transducer.rtf)},")
    appendLine("    \"word_count\": ${transducer.wordCount},")
    appendLine("    \"hotwords_enabled\": $hotwordsEnabled,")
    appendLine("    \"transcript\": \"${jsonEscape(transducerText)}\"")
    appendLine("  }")
    append("}")
}

private fun jsonNumber(value: Double): String {
    require(value.isFinite()) { "JSON numbers must be finite" }
    return String.format(Locale.US, "%.6f", value)
}

private fun jsonEscape(value: String): String = buildString(value.length + 8) {
    value.forEach { character ->
        when (character) {
            '"' -> append("\\\"")
            '\\' -> append("\\\\")
            '\b' -> append("\\b")
            '\u000C' -> append("\\f")
            '\n' -> append("\\n")
            '\r' -> append("\\r")
            '\t' -> append("\\t")
            else -> if (character.code < 0x20) {
                append(String.format(Locale.US, "\\u%04x", character.code))
            } else {
                append(character)
            }
        }
    }
}
