package com.pradeep.speech2text.session

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionJsonTest {

    @Test
    fun testSessionRoundTripSerialization() {
        val run1 = TranscriptionRun(
            id = "run_1",
            engine = "Moonshine Base",
            hotwordsEnabled = false,
            transcript = "Hello world from ASR.",
            inferenceDurationSeconds = 1.25,
            rtf = 0.125,
            wordCount = 4,
            timestamp = 1725300000000L,
        )
        val run2 = TranscriptionRun(
            id = "run_2",
            engine = "Zipformer",
            hotwordsEnabled = true,
            transcript = "Hello world from Zipformer with \"quotes\" & \nnewlines.",
            inferenceDurationSeconds = 0.85,
            rtf = 0.085,
            wordCount = 7,
            timestamp = 1725300100000L,
        )

        val session = TranscriptionSession(
            id = "test-uuid-123",
            createdAt = 1725300000000L,
            modifiedAt = 1725300200000L,
            title = "Recording – 2026-09-03 19:30",
            sourceType = SessionSource.RECORDED,
            audioRelativePath = "audio.wav",
            audioDurationSeconds = 10.0,
            originalTranscript = "Hello world from ASR.",
            currentTranscript = "Hello world! This is user edited text.\nSecond line with unicode: \u00E9 \u2713.",
            isEdited = true,
            engine = "Zipformer",
            inferenceDurationSeconds = 0.85,
            rtf = 0.085,
            wordCount = 11,
            hotwordsEnabled = true,
            runs = listOf(run1, run2),
        )

        val json = SessionJson.sessionToJson(session)
        val parsed = SessionJson.parseSession(json)

        assertEquals(session.id, parsed.id)
        assertEquals(session.createdAt, parsed.createdAt)
        assertEquals(session.modifiedAt, parsed.modifiedAt)
        assertEquals(session.title, parsed.title)
        assertEquals(session.sourceType, parsed.sourceType)
        assertEquals(session.audioRelativePath, parsed.audioRelativePath)
        assertEquals(session.audioDurationSeconds, parsed.audioDurationSeconds, 0.0001)
        assertEquals(session.originalTranscript, parsed.originalTranscript)
        assertEquals(session.currentTranscript, parsed.currentTranscript)
        assertEquals(session.isEdited, parsed.isEdited)
        assertEquals(session.engine, parsed.engine)
        assertEquals(session.inferenceDurationSeconds, parsed.inferenceDurationSeconds, 0.0001)
        assertEquals(session.rtf, parsed.rtf, 0.0001)
        assertEquals(session.wordCount, parsed.wordCount)
        assertEquals(session.hotwordsEnabled, parsed.hotwordsEnabled)
        assertEquals(2, parsed.runs.size)
        assertEquals(run1.id, parsed.runs[0].id)
        assertEquals(run1.transcript, parsed.runs[0].transcript)
        assertEquals(run2.id, parsed.runs[1].id)
        assertEquals(run2.transcript, parsed.runs[1].transcript)
        assertEquals(run2.hotwordsEnabled, parsed.runs[1].hotwordsEnabled)
    }

    @Test
    fun testIndexRoundTripSerialization() {
        val summaries = listOf(
            SessionSummary(
                id = "id-1",
                createdAt = 2000L,
                modifiedAt = 2500L,
                title = "Today Meeting",
                sourceType = SessionSource.RECORDED,
                audioDurationSeconds = 45.0,
                wordCount = 120,
                engine = "Moonshine Base",
                hotwordsEnabled = false,
                isEdited = false,
            ),
            SessionSummary(
                id = "id-2",
                createdAt = 1000L,
                modifiedAt = 1200L,
                title = "Imported – interview.wav",
                sourceType = SessionSource.IMPORTED,
                audioDurationSeconds = 300.0,
                wordCount = 850,
                engine = "Zipformer",
                hotwordsEnabled = true,
                isEdited = true,
            ),
        )

        val json = SessionJson.indexToJson(summaries)
        val parsed = SessionJson.parseIndex(json)

        assertEquals(2, parsed.size)
        assertEquals("id-1", parsed[0].id)
        assertEquals("Today Meeting", parsed[0].title)
        assertEquals(SessionSource.RECORDED, parsed[0].sourceType)
        assertFalse(parsed[0].isEdited)

        assertEquals("id-2", parsed[1].id)
        assertEquals("Imported – interview.wav", parsed[1].title)
        assertEquals(SessionSource.IMPORTED, parsed[1].sourceType)
        assertTrue(parsed[1].isEdited)
        assertTrue(parsed[1].hotwordsEnabled)
    }

    @Test
    fun testEmptyIndex() {
        val json = SessionJson.indexToJson(emptyList())
        val parsed = SessionJson.parseIndex(json)
        assertTrue(parsed.isEmpty())
    }
}
