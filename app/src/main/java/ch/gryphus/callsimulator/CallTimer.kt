package ch.gryphus.callsimulator

import java.util.Locale

/** Pure, framework-free call-duration formatting (unit-tested). */
object CallTimer {

    /**
     * Format an elapsed duration in seconds as mm:ss (minutes zero-padded to at
     * least two digits, seconds always two digits). Negative input is clamped
     * to zero.
     */
    fun format(totalSeconds: Int): String {
        val safe = if (totalSeconds < 0) 0 else totalSeconds
        val minutes = safe / 60
        val seconds = safe % 60
        return String.format(Locale.US, "%02d:%02d", minutes, seconds)
    }
}
