package com.pradeep.speech2text.session

import java.util.Locale

/**
 * Dependency-free, UTF-8 safe JSON serializer and parser for V0.5 sessions.
 * Operates without external libraries to guarantee 100% offline predictability
 * and identical behavior in JVM unit tests and Android runtime.
 */
object SessionJson {

    fun sessionToJson(session: TranscriptionSession): String = buildString {
        appendLine("{")
        appendLine("  \"id\": \"${jsonEscape(session.id)}\",")
        appendLine("  \"created_at\": ${session.createdAt},")
        appendLine("  \"modified_at\": ${session.modifiedAt},")
        appendLine("  \"title\": \"${jsonEscape(session.title)}\",")
        appendLine("  \"source_type\": \"${session.sourceType.name}\",")
        appendLine("  \"audio_relative_path\": \"${jsonEscape(session.audioRelativePath)}\",")
        appendLine("  \"audio_duration_seconds\": ${jsonNumber(session.audioDurationSeconds)},")
        appendLine("  \"original_transcript\": \"${jsonEscape(session.originalTranscript)}\",")
        appendLine("  \"current_transcript\": \"${jsonEscape(session.currentTranscript)}\",")
        appendLine("  \"is_edited\": ${session.isEdited},")
        appendLine("  \"engine\": \"${jsonEscape(session.engine)}\",")
        appendLine("  \"inference_duration_seconds\": ${jsonNumber(session.inferenceDurationSeconds)},")
        appendLine("  \"rtf\": ${jsonNumber(session.rtf)},")
        appendLine("  \"word_count\": ${session.wordCount},")
        appendLine("  \"hotwords_enabled\": ${session.hotwordsEnabled},")
        appendLine("  \"runs\": [")
        session.runs.forEachIndexed { index, run ->
            append("    ${runToJson(run)}")
            if (index < session.runs.lastIndex) appendLine(",") else appendLine()
        }
        appendLine("  ]")
        append("}")
    }

    fun runToJson(run: TranscriptionRun): String = buildString {
        append("{")
        append("\"id\": \"${jsonEscape(run.id)}\", ")
        append("\"engine\": \"${jsonEscape(run.engine)}\", ")
        append("\"hotwords_enabled\": ${run.hotwordsEnabled}, ")
        append("\"transcript\": \"${jsonEscape(run.transcript)}\", ")
        append("\"inference_duration_seconds\": ${jsonNumber(run.inferenceDurationSeconds)}, ")
        append("\"rtf\": ${jsonNumber(run.rtf)}, ")
        append("\"word_count\": ${run.wordCount}, ")
        append("\"timestamp\": ${run.timestamp}")
        append("}")
    }

    fun indexToJson(summaries: List<SessionSummary>): String = buildString {
        appendLine("[")
        summaries.forEachIndexed { index, summary ->
            append("  {")
            append("\"id\": \"${jsonEscape(summary.id)}\", ")
            append("\"created_at\": ${summary.createdAt}, ")
            append("\"modified_at\": ${summary.modifiedAt}, ")
            append("\"title\": \"${jsonEscape(summary.title)}\", ")
            append("\"source_type\": \"${summary.sourceType.name}\", ")
            append("\"audio_duration_seconds\": ${jsonNumber(summary.audioDurationSeconds)}, ")
            append("\"word_count\": ${summary.wordCount}, ")
            append("\"engine\": \"${jsonEscape(summary.engine)}\", ")
            append("\"hotwords_enabled\": ${summary.hotwordsEnabled}, ")
            append("\"is_edited\": ${summary.isEdited}")
            append("}")
            if (index < summaries.lastIndex) appendLine(",") else appendLine()
        }
        append("]")
    }

    @Suppress("UNCHECKED_CAST")
    fun parseSession(json: String): TranscriptionSession {
        val root = parseJson(json) as? Map<String, Any?> ?: error("Invalid session JSON")
        val runsList = (root["runs"] as? List<Map<String, Any?>>)?.map { parseRun(it) } ?: emptyList()

        return TranscriptionSession(
            id = root["id"] as? String ?: error("Missing session id"),
            createdAt = (root["created_at"] as? Number)?.toLong() ?: 0L,
            modifiedAt = (root["modified_at"] as? Number)?.toLong() ?: 0L,
            title = root["title"] as? String ?: "Untitled Session",
            sourceType = parseSourceType(root["source_type"] as? String),
            audioRelativePath = root["audio_relative_path"] as? String ?: "audio.wav",
            audioDurationSeconds = (root["audio_duration_seconds"] as? Number)?.toDouble() ?: 0.0,
            originalTranscript = root["original_transcript"] as? String ?: "",
            currentTranscript = root["current_transcript"] as? String ?: "",
            isEdited = root["is_edited"] as? Boolean ?: false,
            engine = root["engine"] as? String ?: "Unknown Engine",
            inferenceDurationSeconds = (root["inference_duration_seconds"] as? Number)?.toDouble() ?: 0.0,
            rtf = (root["rtf"] as? Number)?.toDouble() ?: 0.0,
            wordCount = (root["word_count"] as? Number)?.toInt() ?: 0,
            hotwordsEnabled = root["hotwords_enabled"] as? Boolean ?: false,
            runs = runsList,
        )
    }

    private fun parseRun(map: Map<String, Any?>): TranscriptionRun = TranscriptionRun(
        id = map["id"] as? String ?: "",
        engine = map["engine"] as? String ?: "",
        hotwordsEnabled = map["hotwords_enabled"] as? Boolean ?: false,
        transcript = map["transcript"] as? String ?: "",
        inferenceDurationSeconds = (map["inference_duration_seconds"] as? Number)?.toDouble() ?: 0.0,
        rtf = (map["rtf"] as? Number)?.toDouble() ?: 0.0,
        wordCount = (map["word_count"] as? Number)?.toInt() ?: 0,
        timestamp = (map["timestamp"] as? Number)?.toLong() ?: 0L,
    )

    @Suppress("UNCHECKED_CAST")
    fun parseIndex(json: String): List<SessionSummary> {
        val root = parseJson(json) as? List<Map<String, Any?>> ?: return emptyList()
        return root.mapNotNull { item ->
            val id = item["id"] as? String ?: return@mapNotNull null
            SessionSummary(
                id = id,
                createdAt = (item["created_at"] as? Number)?.toLong() ?: 0L,
                modifiedAt = (item["modified_at"] as? Number)?.toLong() ?: 0L,
                title = item["title"] as? String ?: "Untitled Session",
                sourceType = parseSourceType(item["source_type"] as? String),
                audioDurationSeconds = (item["audio_duration_seconds"] as? Number)?.toDouble() ?: 0.0,
                wordCount = (item["word_count"] as? Number)?.toInt() ?: 0,
                engine = item["engine"] as? String ?: "Unknown Engine",
                hotwordsEnabled = item["hotwords_enabled"] as? Boolean ?: false,
                isEdited = item["is_edited"] as? Boolean ?: false,
            )
        }
    }

    private fun parseSourceType(value: String?): SessionSource = when (value) {
        SessionSource.IMPORTED.name -> SessionSource.IMPORTED
        else -> SessionSource.RECORDED
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

    /**
     * Lightweight recursive-descent JSON parser.
     * Parses Objects into Map<String, Any?> and Arrays into List<Any?>.
     */
    fun parseJson(json: String): Any? {
        val parser = SimpleJsonParser(json.trim())
        return parser.parseValue()
    }

    private class SimpleJsonParser(private val text: String) {
        private var pos = 0

        fun parseValue(): Any? {
            skipWhitespace()
            if (pos >= text.length) return null
            return when (val c = text[pos]) {
                '{' -> parseObject()
                '[' -> parseArray()
                '"' -> parseString()
                't', 'f' -> parseBoolean()
                'n' -> parseNull()
                else -> if (c == '-' || c in '0'..'9') parseNumber() else error("Unexpected character '$c' at position $pos")
            }
        }

        private fun parseObject(): Map<String, Any?> {
            expect('{')
            val result = mutableMapOf<String, Any?>()
            skipWhitespace()
            if (peek() == '}') {
                pos++
                return result
            }
            while (true) {
                skipWhitespace()
                val key = parseString()
                skipWhitespace()
                expect(':')
                val value = parseValue()
                result[key] = value
                skipWhitespace()
                when (peek()) {
                    ',' -> pos++
                    '}' -> {
                        pos++
                        return result
                    }
                    else -> error("Expected ',' or '}' at position $pos, found '${peek()}'")
                }
            }
        }

        private fun parseArray(): List<Any?> {
            expect('[')
            val result = mutableListOf<Any?>()
            skipWhitespace()
            if (peek() == ']') {
                pos++
                return result
            }
            while (true) {
                val value = parseValue()
                result.add(value)
                skipWhitespace()
                when (peek()) {
                    ',' -> pos++
                    ']' -> {
                        pos++
                        return result
                    }
                    else -> error("Expected ',' or ']' at position $pos, found '${peek()}'")
                }
            }
        }

        private fun parseString(): String {
            expect('"')
            val sb = StringBuilder()
            while (pos < text.length) {
                val c = text[pos++]
                if (c == '"') return sb.toString()
                if (c == '\\') {
                    if (pos >= text.length) error("Unterminated escape at position $pos")
                    when (val esc = text[pos++]) {
                        '"' -> sb.append('"')
                        '\\' -> sb.append('\\')
                        '/' -> sb.append('/')
                        'b' -> sb.append('\b')
                        'f' -> sb.append('\u000C')
                        'n' -> sb.append('\n')
                        'r' -> sb.append('\r')
                        't' -> sb.append('\t')
                        'u' -> {
                            if (pos + 4 > text.length) error("Invalid unicode escape at position $pos")
                            val hex = text.substring(pos, pos + 4)
                            pos += 4
                            sb.append(hex.toInt(16).toChar())
                        }
                        else -> sb.append(esc)
                    }
                } else {
                    sb.append(c)
                }
            }
            error("Unterminated string starting before position $pos")
        }

        private fun parseNumber(): Number {
            val start = pos
            if (text[pos] == '-') pos++
            while (pos < text.length && text[pos] in '0'..'9') pos++
            var isDouble = false
            if (pos < text.length && text[pos] == '.') {
                isDouble = true
                pos++
                while (pos < text.length && text[pos] in '0'..'9') pos++
            }
            if (pos < text.length && (text[pos] == 'e' || text[pos] == 'E')) {
                isDouble = true
                pos++
                if (pos < text.length && (text[pos] == '+' || text[pos] == '-')) pos++
                while (pos < text.length && text[pos] in '0'..'9') pos++
            }
            val numStr = text.substring(start, pos)
            return if (isDouble) {
                numStr.toDouble()
            } else {
                numStr.toLongOrNull() ?: numStr.toDouble()
            }
        }

        private fun parseBoolean(): Boolean {
            return if (text.startsWith("true", pos)) {
                pos += 4
                true
            } else if (text.startsWith("false", pos)) {
                pos += 5
                false
            } else {
                error("Invalid boolean at position $pos")
            }
        }

        private fun parseNull(): Any? {
            if (text.startsWith("null", pos)) {
                pos += 4
                return null
            }
            error("Invalid null at position $pos")
        }

        private fun skipWhitespace() {
            while (pos < text.length && text[pos].isWhitespace()) pos++
        }

        private fun expect(expected: Char) {
            skipWhitespace()
            if (pos >= text.length || text[pos] != expected) {
                error("Expected '$expected' at position $pos, found '${peek()}'")
            }
            pos++
        }

        private fun peek(): Char = if (pos < text.length) text[pos] else '\u0000'
    }
}
