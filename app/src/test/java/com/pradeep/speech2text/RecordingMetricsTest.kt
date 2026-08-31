package com.pradeep.speech2text

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RecordingMetricsTest {
    @Test
    fun rmsNormalizationKeepsSilenceFlatAndLoudSignalHigh() {
        assertEquals(0f, RecordingMetrics.rmsLevel(ShortArray(100), 100), 0.001f)
        assertTrue(RecordingMetrics.rmsLevel(ShortArray(100) { Short.MAX_VALUE }, 100) > 0.98f)
    }

    @Test
    fun smoothingBlendsPreviousAndCurrentLevel() {
        assertEquals(0.65f, RecordingMetrics.smooth(0f, 1f), 0.0001f)
        assertEquals(0.88f, RecordingMetrics.smooth(1f, 0f), 0.0001f)
    }

    @Test
    fun durationFormattingAndLimitAreBounded() {
        assertEquals("00:07", RecordingMetrics.formatDuration(7_000))
        assertEquals("59:59", RecordingMetrics.formatDuration(3_599_000))
        assertEquals("60:00", RecordingMetrics.formatDuration(3_600_000))
        assertTrue(RecordingMetrics.reachedLimit(100L, 3_700L, 3_600L))
    }
}
