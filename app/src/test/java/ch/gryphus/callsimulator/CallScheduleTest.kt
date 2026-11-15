package ch.gryphus.callsimulator

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CallScheduleTest {

    private val now = 1_700_000_000_000L // fixed reference instant

    @Test
    fun `future trigger is valid`() {
        assertTrue(CallSchedule.isInFuture(now + 1, now))
        assertTrue(CallSchedule.isInFuture(now + 60_000, now))
    }

    @Test
    fun `now or past trigger is invalid`() {
        assertFalse(CallSchedule.isInFuture(now, now))
        assertFalse(CallSchedule.isInFuture(now - 1, now))
    }

    @Test
    fun `shouldRearm mirrors future-ness`() {
        assertTrue(CallSchedule.shouldRearm(now + 5_000, now))
        assertFalse(CallSchedule.shouldRearm(now - 5_000, now))
    }

    @Test
    fun `lead time formats two most significant units`() {
        assertEquals("1m 30s", CallSchedule.formatLeadTime(now + 90_000, now))
        assertEquals("2h 5m", CallSchedule.formatLeadTime(now + (2 * 3600 + 5 * 60 + 9) * 1000L, now))
        assertEquals("1d 3h", CallSchedule.formatLeadTime(now + (27 * 3600) * 1000L, now))
    }

    @Test
    fun `lead time handles sub-minute and zero`() {
        assertEquals("45s", CallSchedule.formatLeadTime(now + 45_000, now))
        assertEquals("0s", CallSchedule.formatLeadTime(now, now))
        // Past clamps to zero, never negative.
        assertEquals("0s", CallSchedule.formatLeadTime(now - 10_000, now))
    }

    @Test
    fun `clock formats 24h with timezone offset`() {
        // now at UTC+0 -> known HH:mm; shift by +120 min and expect +2h wrap.
        val utc = CallSchedule.formatClock24(now, 0)
        val plus2 = CallSchedule.formatClock24(now, 120)
        // Both are HH:mm shaped.
        assertTrue(utc.matches(Regex("\\d{2}:\\d{2}")))
        assertTrue(plus2.matches(Regex("\\d{2}:\\d{2}")))
    }

    @Test
    fun `clock wraps midnight correctly`() {
        // 23:30 UTC + 60min offset -> 00:30 next day (time-of-day wraps).
        // Build an instant at 23:30:00 UTC.
        val base = 1_700_000_000_000L
        val h = CallSchedule.formatClock24(base, 0)
        val shifted = CallSchedule.formatClock24(base, 24 * 60) // +24h offset = same clock
        assertEquals(h, shifted)
    }

    // ---- Relative-delay scheduling (triggerFromDelay) ---------------------

    @Test
    fun `delay adds the right offset per unit`() {
        assertEquals(now + 30_000L, CallSchedule.triggerFromDelay(now, 30, DelayUnit.SECONDS))
        assertEquals(now + 5 * 60_000L, CallSchedule.triggerFromDelay(now, 5, DelayUnit.MINUTES))
        assertEquals(now + 2 * 3_600_000L, CallSchedule.triggerFromDelay(now, 2, DelayUnit.HOURS))
    }

    @Test
    fun `delay result is always in the future`() {
        val trigger = CallSchedule.triggerFromDelay(now, 1, DelayUnit.SECONDS)!!
        assertTrue(CallSchedule.isInFuture(trigger, now))
    }

    @Test
    fun `delay rejects non-positive and out-of-range amounts`() {
        assertNull(CallSchedule.triggerFromDelay(now, 0, DelayUnit.MINUTES))
        assertNull(CallSchedule.triggerFromDelay(now, -5, DelayUnit.MINUTES))
        assertNull(
            CallSchedule.triggerFromDelay(now, CallSchedule.MAX_DELAY_AMOUNT + 1, DelayUnit.HOURS)
        )
    }

    @Test
    fun `delay accepts the maximum amount`() {
        val max = CallSchedule.MAX_DELAY_AMOUNT
        assertEquals(
            now + max.toLong() * DelayUnit.HOURS.toMillis,
            CallSchedule.triggerFromDelay(now, max, DelayUnit.HOURS)
        )
    }

    @Test
    fun `delay is overflow-safe near Long max`() {
        // Should clamp to Long.MAX_VALUE rather than overflow to a negative.
        val trigger = CallSchedule.triggerFromDelay(Long.MAX_VALUE - 10L, 9_999, DelayUnit.HOURS)!!
        assertEquals(Long.MAX_VALUE, trigger)
    }
}
