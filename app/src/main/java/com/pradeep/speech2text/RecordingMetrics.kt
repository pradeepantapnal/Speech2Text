package com.pradeep.speech2text

import kotlin.math.log10
import kotlin.math.sqrt

internal object RecordingMetrics {
    fun rmsLevel(samples: ShortArray, count: Int): Float {
        if (count <= 0) return 0f
        var sumSquares = 0.0
        for (index in 0 until count) {
            val normalized = samples[index] / Short.MAX_VALUE.toDouble()
            sumSquares += normalized * normalized
        }
        val rms = sqrt(sumSquares / count)
        // A 60 dB display range makes normal speech visible while keeping silence flat.
        val dbfs = if (rms <= 0.000001) -120.0 else 20.0 * log10(rms)
        return ((dbfs + 60.0) / 60.0).coerceIn(0.0, 1.0).toFloat()
    }

    /** Fast attack for speech onset, slower release for a steadier indicator. */
    fun smooth(previous: Float, current: Float): Float = if (current >= previous) {
        previous * 0.35f + current * 0.65f
    } else {
        previous * 0.88f + current * 0.12f
    }

    fun formatDuration(elapsedMs: Long): String {
        val totalSeconds = (elapsedMs.coerceAtLeast(0L) / 1_000L).coerceAtMost(60 * 60L)
        return "%02d:%02d".format(totalSeconds / 60, totalSeconds % 60)
    }

    fun reachedLimit(startElapsedRealtime: Long, nowElapsedRealtime: Long, limitMs: Long): Boolean =
        nowElapsedRealtime - startElapsedRealtime >= limitMs
}
