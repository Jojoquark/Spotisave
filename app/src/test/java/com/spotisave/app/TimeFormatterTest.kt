package com.spotisave.app

import com.spotisave.app.ui.util.TimeFormatter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TimeFormatterTest {

    @Test
    fun testFormatTimeZeroAndNegative() {
        assertEquals("0:00", TimeFormatter.formatTime(0L))
        assertEquals("0:00", TimeFormatter.formatTime(-1000L))
    }

    @Test
    fun testFormatTimeMinutesSeconds() {
        assertEquals("0:05", TimeFormatter.formatTime(5000L))
        assertEquals("1:05", TimeFormatter.formatTime(65000L))
        assertEquals("10:00", TimeFormatter.formatTime(600000L))
    }

    @Test
    fun testFormatTimeHours() {
        assertEquals("1:01:05", TimeFormatter.formatTime(3665000L))
        assertEquals("2:30:15", TimeFormatter.formatTime(9015000L))
    }

    @Test
    fun testFormatRelativeTime() {
        val now = System.currentTimeMillis()
        assertEquals("gerade eben", TimeFormatter.formatRelativeTime(now - 1000L))
        assertEquals("vor 30s", TimeFormatter.formatRelativeTime(now - 30_000L))
        assertEquals("vor 5 Min.", TimeFormatter.formatRelativeTime(now - 300_000L))
        assertEquals("vor 2 Std.", TimeFormatter.formatRelativeTime(now - 7_200_000L))
        assertEquals("vor 3 Tagen", TimeFormatter.formatRelativeTime(now - 3 * 86_400_000L))
        assertEquals("unbekannt", TimeFormatter.formatRelativeTime(0L))
    }
}
