package com.pradeep.speech2text.session

import com.pradeep.speech2text.BenchmarkMetrics
import com.pradeep.speech2text.PcmAudio
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class SessionRepositoryTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var repository: SessionRepository

    private val sampleAudio = PcmAudio(
        samples = ShortArray(16000) { 100 }, // 1 second of 16kHz mono audio
        sampleRate = 16000,
        channels = 1,
    )

    private val sampleMetrics = BenchmarkMetrics(
        audioDurationSeconds = 1.0,
        inferenceDurationSeconds = 0.1,
        rtf = 0.1,
        wordCount = 3,
        modelName = "Moonshine Base",
        backendInfo = "sherpa-onnx / CPU",
        deviceAbi = "arm64-v8a",
    )

    @Before
    fun setup() {
        repository = SessionRepository(tempFolder.root)
    }

    @Test
    fun testCreateSessionAndRetrieve() {
        val session = repository.createSession(
            audio = sampleAudio,
            title = "Recording – 2026-09-03 19:40",
            sourceType = SessionSource.RECORDED,
            transcript = "This is test.",
            engine = "Moonshine Base",
            hotwordsEnabled = false,
            metrics = sampleMetrics,
        )

        assertNotNull(session.id)
        assertEquals("Recording – 2026-09-03 19:40", session.title)
        assertEquals("This is test.", session.originalTranscript)
        assertEquals("This is test.", session.currentTranscript)
        assertFalse(session.isEdited)
        assertEquals(1, session.runs.size)

        // Retrieve full session
        val loaded = repository.getSession(session.id)
        assertNotNull(loaded)
        assertEquals(session.id, loaded?.id)
        assertEquals("This is test.", loaded?.currentTranscript)

        // Retrieve audio
        val audio = repository.getAudio(session.id)
        assertNotNull(audio)
        assertEquals(sampleAudio.sampleRate, audio?.sampleRate)
        assertEquals(sampleAudio.frameCount, audio?.frameCount)

        // Retrieve summaries
        val summaries = repository.getAllSummaries()
        assertEquals(1, summaries.size)
        assertEquals(session.id, summaries[0].id)
    }

    @Test
    fun testUpdateTranscriptPreservesOriginal() {
        val session = repository.createSession(
            audio = sampleAudio,
            title = "Interview Notes",
            sourceType = SessionSource.RECORDED,
            transcript = "Raw voice recognition text.",
            engine = "Moonshine Base",
            hotwordsEnabled = false,
            metrics = sampleMetrics,
        )

        val edited = repository.updateTranscript(
            id = session.id,
            newTranscript = "Edited voice recognition text with corrections.",
        )

        assertNotNull(edited)
        assertTrue(edited!!.isEdited)
        assertEquals("Raw voice recognition text.", edited.originalTranscript)
        assertEquals("Edited voice recognition text with corrections.", edited.currentTranscript)
        assertEquals(6, edited.wordCount)

        // Verify loaded from disk
        val loaded = repository.getSession(session.id)
        assertTrue(loaded!!.isEdited)
        assertEquals("Raw voice recognition text.", loaded.originalTranscript)
        assertEquals("Edited voice recognition text with corrections.", loaded.currentTranscript)
        assertEquals(6, loaded.wordCount)

        // Restore original
        val restored = repository.restoreOriginalTranscript(session.id)
        assertNotNull(restored)
        assertFalse(restored!!.isEdited)
        assertEquals("Raw voice recognition text.", restored.currentTranscript)
        assertEquals("Raw voice recognition text.", restored.originalTranscript)
        assertEquals(4, restored.wordCount)
    }

    @Test
    fun testRenameSession() {
        val session = repository.createSession(
            audio = sampleAudio,
            title = "Default Title",
            sourceType = SessionSource.RECORDED,
            transcript = "Hello",
            engine = "Moonshine Base",
            hotwordsEnabled = false,
            metrics = sampleMetrics,
        )

        val renamed = repository.renameSession(session.id, "My Custom Lecture")
        assertNotNull(renamed)
        assertEquals("My Custom Lecture", renamed?.title)

        val summaries = repository.getAllSummaries()
        assertEquals("My Custom Lecture", summaries[0].title)
    }

    @Test
    fun testHistoryOrderingAndSearch() {
        val s1 = repository.createSession(
            audio = sampleAudio,
            title = "Platform diagnostics discussion",
            sourceType = SessionSource.RECORDED,
            transcript = "The system encountered latency during disk write operations.",
            engine = "Moonshine Base",
            hotwordsEnabled = false,
            metrics = sampleMetrics,
        )
        // Ensure s2 has slightly later timestamp
        Thread.sleep(10)

        val s2 = repository.createSession(
            audio = sampleAudio,
            title = "Morning Standup",
            sourceType = SessionSource.RECORDED,
            transcript = "Everything is progressing normally today.",
            engine = "Zipformer",
            hotwordsEnabled = true,
            metrics = sampleMetrics,
        )

        val all = repository.getAllSummaries()
        assertEquals(2, all.size)
        // Reverse chronological order: s2 (newer) comes before s1 (older)
        assertEquals(s2.id, all[0].id)
        assertEquals(s1.id, all[1].id)

        // Search by title
        val titleMatches = repository.search("Standup")
        assertEquals(1, titleMatches.size)
        assertEquals(s2.id, titleMatches[0].id)

        // Search by transcript text
        val transcriptMatches = repository.search("latency")
        assertEquals(1, transcriptMatches.size)
        assertEquals(s1.id, transcriptMatches[0].id)

        // Empty search returns all
        assertEquals(2, repository.search("").size)
    }

    @Test
    fun testAddTranscriptionRun() {
        val session = repository.createSession(
            audio = sampleAudio,
            title = "Audio Note",
            sourceType = SessionSource.RECORDED,
            transcript = "Run 1 Moonshine text",
            engine = "Moonshine Base",
            hotwordsEnabled = false,
            metrics = sampleMetrics,
        )

        val newRun = TranscriptionRun(
            id = "run_2",
            engine = "Zipformer",
            hotwordsEnabled = true,
            transcript = "Run 2 Zipformer text",
            inferenceDurationSeconds = 0.05,
            rtf = 0.05,
            wordCount = 4,
            timestamp = System.currentTimeMillis(),
        )

        val updated = repository.addRun(session.id, newRun, updateCurrentIfUnedited = true)
        assertNotNull(updated)
        assertEquals(2, updated!!.runs.size)
        assertEquals("Zipformer", updated.engine)
        assertEquals("Run 2 Zipformer text", updated.currentTranscript)

        // Now edit transcript manually
        repository.updateTranscript(session.id, "User Edited text")

        // Add run 3 with updateCurrentIfUnedited = true -> should NOT overwrite user edits!
        val run3 = TranscriptionRun(
            id = "run_3",
            engine = "Moonshine Base",
            hotwordsEnabled = false,
            transcript = "Run 3 text",
            inferenceDurationSeconds = 0.08,
            rtf = 0.08,
            wordCount = 3,
            timestamp = System.currentTimeMillis(),
        )
        val afterRun3 = repository.addRun(session.id, run3, updateCurrentIfUnedited = true)
        assertEquals(3, afterRun3!!.runs.size)
        assertEquals("User Edited text", afterRun3.currentTranscript)
        assertTrue(afterRun3.isEdited)
    }

    @Test
    fun testDeleteSession() {
        val session = repository.createSession(
            audio = sampleAudio,
            title = "To be deleted",
            sourceType = SessionSource.RECORDED,
            transcript = "Delete me",
            engine = "Moonshine Base",
            hotwordsEnabled = false,
            metrics = sampleMetrics,
        )

        assertEquals(1, repository.getAllSummaries().size)
        val deleted = repository.deleteSession(session.id)
        assertTrue(deleted)
        assertTrue(repository.getAllSummaries().isEmpty())
        assertNull(repository.getSession(session.id))
        assertNull(repository.getAudio(session.id))
    }

    @Test
    fun testReloadAcrossRepositoryInstances() {
        val s1 = repository.createSession(
            audio = sampleAudio,
            title = "Session across restart",
            sourceType = SessionSource.RECORDED,
            transcript = "Persisted text across app kill",
            engine = "Moonshine Base",
            hotwordsEnabled = false,
            metrics = sampleMetrics,
        )
        repository.updateTranscript(s1.id, "Edited text across restart")

        // Simulate app process kill and restart by creating a new repository on same directory
        val reloadedRepository = SessionRepository(tempFolder.root)
        val summaries = reloadedRepository.getAllSummaries()
        assertEquals(1, summaries.size)
        assertEquals(s1.id, summaries[0].id)
        assertEquals("Session across restart", summaries[0].title)
        assertTrue(summaries[0].isEdited)

        val fullSession = reloadedRepository.getSession(s1.id)
        assertNotNull(fullSession)
        assertEquals("Edited text across restart", fullSession?.currentTranscript)
        assertEquals("Persisted text across app kill", fullSession?.originalTranscript)

        val reloadedAudio = reloadedRepository.getAudio(s1.id)
        assertNotNull(reloadedAudio)
        assertEquals(sampleAudio.frameCount, reloadedAudio?.frameCount)
    }
}
