package com.prostellis.awe

import com.prostellis.awe.widget.ClockColor
import com.prostellis.awe.widget.ClockFace
import com.prostellis.awe.widget.ClockPalette
import com.prostellis.awe.widget.ClockRenderer
import com.prostellis.awe.widget.ColorFamily
import com.prostellis.awe.widget.TimeFormat
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone

class ClockTest {

    private fun at(h: Int, m: Int): Long {
        TimeZone.setDefault(TimeZone.getTimeZone("America/Los_Angeles"))
        return Calendar.getInstance().apply { set(2026, Calendar.OCTOBER, 14, h, m, 0) }.timeInMillis
    }

    @Test fun timeHasNoAmOrPmAndFollowsTheFormat() {
        assertEquals("4:27", ClockRenderer.timeText(at(16, 27), TimeFormat.H12, phone24 = true))
        assertEquals("16:27", ClockRenderer.timeText(at(16, 27), TimeFormat.H24, phone24 = false))
        assertEquals("12:05", ClockRenderer.timeText(at(0, 5), TimeFormat.H12, phone24 = false))
        assertEquals("00:05", ClockRenderer.timeText(at(0, 5), TimeFormat.H24, phone24 = false))
        assertEquals("12:00", ClockRenderer.timeText(at(12, 0), TimeFormat.PHONE, phone24 = false))
        assertEquals("09:30", ClockRenderer.timeText(at(9, 30), TimeFormat.PHONE, phone24 = true))
    }

    @Test fun pickerHasTheApprovedPresets() {
        assertEquals(16, ClockPalette.SOLID.size)
        assertEquals("Auto", ClockPalette.SOLID[0].first)
        assertEquals(12, ClockPalette.METAL.size)
        assertEquals(12, ClockPalette.GRAD.size)
        ClockPalette.METAL.forEach { assertEquals(it.first, 5, it.second.size) }
        assertEquals(10, ClockFace.ALL.size)
        assertEquals(10, ClockFace.ALL.map { it.id }.toSet().size)
    }

    @Test fun colorsSurviveSaving() {
        val colors = listOf(ClockColor.AUTO, ClockColor(ColorFamily.SOLID, 15), ClockColor(ColorFamily.METAL, 4),
            ClockColor(ColorFamily.GRAD, 11), ClockColor(ColorFamily.CUSTOM, hex = 0xFFFFC200.toInt()))
        for (c in colors) assertEquals(c, ClockColor.decode(c.encode()))
        assertEquals("custom:ffc200", ClockColor(ColorFamily.CUSTOM, hex = 0xFFFFC200.toInt()).encode())
        // Anything unreadable falls back to Auto
        for (bad in listOf(null, "", "metal:99", "plaid:1", "custom:zz")) assertEquals(ClockColor.AUTO, ClockColor.decode(bad))
        assertEquals("Metallic · Blue", ClockPalette.label(ClockColor(ColorFamily.METAL, 4)))
        assertEquals("Custom · #FFC200", ClockPalette.label(ClockColor(ColorFamily.CUSTOM, hex = 0xFFFFC200.toInt())))
    }
}
