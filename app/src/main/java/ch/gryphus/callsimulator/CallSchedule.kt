package ch.gryphus.callsimulator

import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * Framework-free scheduling logic for a future simulated call.
 *
 * Kept independent of the Android framework (no Context, AlarmManager, or
 * Calendar plumbing) so every decision - validity, formatting, re-arm on boot -
 * is covered by fast JVM unit tests. The Android pieces ([CallScheduler],
 * [CallAlarmReceiver], [BootReceiver]) only translate these results into
 * AlarmManager calls.
 */
data class ScheduledCall(
    val name: String,
    val number: String,
    val photo: String?,
    /** Absolute fire time, epoch millis. */
    val triggerAtMillis: Long,
)

object CallSchedule {

    /**
     * A schedule is valid only if it fires strictly in the future (with a small
     * guard so a trigger a few ms away isn't armed into the past).
     */
    fun isInFuture(triggerAtMillis: Long, nowMillis: Long): Boolean =
        triggerAtMillis > nowMillis

    /**
     * Should a persisted schedule still be armed (e.g. after a reboot)? Only if
     * its trigger time hasn't already passed.
     */
    fun shouldRearm(triggerAtMillis: Long, nowMillis: Long): Boolean =
        isInFuture(triggerAtMillis, nowMillis)

    /**
     * Human-readable "fires in ..." lead time for a confirmation message.
     * Clamped to zero; rendered as compact d/h/m/s with the two most
     * significant non-zero units.
     */
    fun formatLeadTime(triggerAtMillis: Long, nowMillis: Long): String {
        val deltaMs = (triggerAtMillis - nowMillis).coerceAtLeast(0)
        val totalSeconds = TimeUnit.MILLISECONDS.toSeconds(deltaMs)
        val days = totalSeconds / 86_400
        val hours = (totalSeconds % 86_400) / 3_600
        val minutes = (totalSeconds % 3_600) / 60
        val seconds = totalSeconds % 60

        val parts = buildList {
            if (days > 0) add("${days}d")
            if (hours > 0) add("${hours}h")
            if (minutes > 0) add("${minutes}m")
            if (seconds > 0) add("${seconds}s")
        }
        return when {
            parts.isEmpty() -> "0s"
            else -> parts.take(2).joinToString(" ")
        }
    }

    /**
     * Format an absolute epoch-millis time as 24-hour "HH:mm" in the supplied
     * timezone offset (minutes east of UTC). Framework-free so it is testable;
     * the UI can still prefer a localized formatter for display.
     */
    fun formatClock24(triggerAtMillis: Long, utcOffsetMinutes: Int): String {
        val local = triggerAtMillis + utcOffsetMinutes * 60_000L
        val minutesOfDay = Math.floorMod(local / 60_000L, 1_440L).toInt()
        val hh = minutesOfDay / 60
        val mm = minutesOfDay % 60
        return String.format(Locale.US, "%02d:%02d", hh, mm)
    }
}
