package com.prostellis.awe

import com.prostellis.awe.weather.Forecast
import com.prostellis.awe.weather.NwsBackup
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NwsBackupTest {
    private fun period(start: String, temp: Int, day: Boolean, text: String) =
        JSONObject().put("startTime", start).put("temperature", temp).put("isDaytime", day).put("shortForecast", text)

    private fun wrap(vararg p: JSONObject) = JSONObject().put("properties", JSONObject().put("periods", JSONArray(p.toList()))).toString()

    @Test
    fun weatherServiceForecastBecomesAWidgetForecast() {
        val hourly = wrap(
            period("2026-11-14T18:00:00-08:00", 50, false, "Light Rain"),
            period("2026-11-14T19:00:00-08:00", 48, false, "Rain"),
            period("2026-11-15T12:00:00-08:00", 57, true, "Mostly Cloudy"),
            period("2026-11-15T23:00:00-08:00", 41, false, "Cloudy"),
        )
        val halves = wrap(
            period("2026-11-14T18:00:00-08:00", 44, false, "Rain"),
            period("2026-11-15T06:00:00-08:00", 58, true, "Partly Sunny"),
            period("2026-11-16T06:00:00-08:00", 52, true, "Chance Rain Showers"),
            period("2026-11-16T18:00:00-08:00", 38, false, "Mostly Clear"),
        )
        val f = Forecast.parse(NwsBackup.fromPeriods(hourly, halves, null))
        assertEquals(-8 * 3600, f.utcOffsetSeconds)
        assertEquals(50.0, f.current.temp, 0.0)
        assertEquals(61, f.current.code) // light rain
        assertEquals(listOf("2026-11-14", "2026-11-15", "2026-11-16"), f.daily.time)
        assertEquals(58.0, f.daily.max[1]!!, 0.0) // the 12-hour high tops the hourly ones on the last hourly day
        assertEquals(38.0, f.daily.min[2]!!, 0.0)
        assertEquals(2, f.daily.code[1]) // partly sunny
        assertEquals(80, f.daily.code[2]) // showers
        assertNull(f.daily.precip[0])
        assertEquals("2026-11-15T07:00", f.daily.sunrise[1])
        assertEquals(4, f.hourly.time.size)
    }
}
