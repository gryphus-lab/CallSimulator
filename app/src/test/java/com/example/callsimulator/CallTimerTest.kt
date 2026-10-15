package com.example.callsimulator

import org.junit.Assert.assertEquals
import org.junit.Test

class CallTimerTest {

    @Test
    fun format_zero() {
        assertEquals("00:00", CallTimer.format(0))
    }

    @Test
    fun format_secondsOnly() {
        assertEquals("00:05", CallTimer.format(5))
        assertEquals("00:59", CallTimer.format(59))
    }

    @Test
    fun format_minutesAndSeconds() {
        assertEquals("01:00", CallTimer.format(60))
        assertEquals("01:05", CallTimer.format(65))
        assertEquals("02:03", CallTimer.format(123))
    }

    @Test
    fun format_largeDurationKeepsAllMinutes() {
        // 100 minutes + 1s -> minutes not truncated to two digits.
        assertEquals("100:01", CallTimer.format(6001))
    }

    @Test
    fun format_negativeClampsToZero() {
        assertEquals("00:00", CallTimer.format(-1))
        assertEquals("00:00", CallTimer.format(-120))
    }
}
