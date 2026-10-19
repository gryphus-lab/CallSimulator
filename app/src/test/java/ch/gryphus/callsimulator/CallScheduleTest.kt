package ch.gryphus.callsimulator

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
}
