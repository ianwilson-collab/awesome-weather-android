package com.prostellis.awe

import com.prostellis.awe.weather.Cat
import com.prostellis.awe.weather.Daylight
import com.prostellis.awe.weather.Forecast
import com.prostellis.awe.weather.Place
import com.prostellis.awe.weather.SkyPalette
import com.prostellis.awe.weather.WeatherLogic
import com.prostellis.awe.widget.WidgetRenderer
import com.prostellis.awe.widget.WidgetTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class WeatherLogicTest {

    private val forecast = Forecast.parse(javaClass.getResource("/forecast_salem.json")!!.readText())

    /** Location-local time in Salem (UTC-7 in October) as epoch millis. */
    private fun salem(local: String) = Instant.parse("$local:00Z").toEpochMilli() + 7 * 3600_000L

    @Test fun parsesCurrentConditions() {
        assertEquals(71.2, forecast.current.temp, 0.0)
        assertEquals(2, forecast.current.code)
        assertEquals(-25200, forecast.utcOffsetSeconds)
    }

    @Test fun buildsSevenDaysWithOvernightLowsLikeTheWebApp() {
        val days = WeatherLogic.buildDays(forecast, salem("2026-10-08T09:35"), 7)
        assertEquals(listOf("2026-10-08", "2026-10-09", "2026-10-10", "2026-10-11", "2026-10-12", "2026-10-13", "2026-10-14"), days.map { it.ymd })
        assertEquals(listOf(78, 63, 60, 63, 66, 66, 66), days.map { WeatherLogic.jsRound(it.hi!!) })
        // Lows come from 6pm–8am hourly temperatures, not the daily minimum (which is a degree lower in the sample)
        assertEquals(listOf(52, 48, 46, 44, 42, 43, 48), days.map { WeatherLogic.jsRound(it.lo!!) })
        assertEquals(listOf(Cat.PARTLY, Cat.RAIN, Cat.RAIN, Cat.PARTLY, Cat.CLEAR, Cat.DRIZZLE, Cat.PARTLY), days.map { it.cat })
    }

    @Test fun todayFollowsTheClockNotTheDownload() {
        val days = WeatherLogic.buildDays(forecast, salem("2026-10-10T08:00"), 5)
        assertEquals("2026-10-10", days.first().ymd)
    }

    @Test fun overnightLowNeedsTenHours() {
        assertNull(WeatherLogic.overnightLow(forecast, "2026-10-15")) // only 6 hours left in the data
    }

    @Test fun precipitationCountsMoreWhenPickingTheDayIcon() {
        val json = """{"utc_offset_seconds":0,
            "current":{"time":"2026-01-01T09:00","temperature_2m":40,"apparent_temperature":38,"weather_code":3,"is_day":1},
            "daily":{"time":["2026-01-01"],"weather_code":[3],"temperature_2m_max":[45],"temperature_2m_min":[35],
              "precipitation_probability_max":[40],"precipitation_sum":[0.1],"sunrise":["2026-01-01T07:50"],"sunset":["2026-01-01T16:40"]},
            "hourly":{"time":[${(0..23).joinToString(",") { "\"2026-01-01T%02d:00\"".format(it) }}],
              "temperature_2m":[${List(24) { 40 }.joinToString(",")}],
              "weather_code":[${(0..23).joinToString(",") { if (it in 7..12) "61" else "3" }}]}}"""
        // 6 rainy daytime hours × 1.6 = 9.6 outweigh 7 cloudy daytime hours × 1.0 = 7
        assertEquals(Cat.RAIN, WeatherLogic.dominantCat(Forecast.parse(json), "2026-01-01"))
    }

    @Test fun daylightMatchesTheWebAppsTwilightWindows() {
        val morning = WeatherLogic.daylight(forecast, salem("2026-10-08T09:35"))
        assertEquals(1.0, morning.d, 0.0); assertTrue(morning.isDay)
        val night = WeatherLogic.daylight(forecast, salem("2026-10-08T21:40"))
        assertEquals(0.0, night.d, 0.0); assertFalse(night.isDay)
        val dawn = WeatherLogic.daylight(forecast, salem("2026-10-08T07:00"))
        assertEquals((420 - 380) / 75.0, dawn.d, 1e-9); assertTrue(dawn.rising); assertFalse(dawn.isDay)
    }

    @Test fun skyColorsUseTheWebAppPalette() {
        val day = Daylight(1.0, false, true)
        assertEquals(0xFFC9DEF5.toInt() to 0xFFF4F7FB.toInt(), WeatherLogic.skyColors(Cat.PARTLY, day, dark = false))
        assertEquals(0xFF22384F.toInt() to 0xFF2C3A4A.toInt(), WeatherLogic.skyColors(Cat.PARTLY, day, dark = true))
        val night = Daylight(0.0, false, false)
        assertEquals(SkyPalette.LIGHT.night[0] to SkyPalette.LIGHT.night[1], WeatherLogic.skyColors(Cat.CLEAR, night, dark = false))
        // Cloudy skies keep 45% of their own color even at night
        val rainNight = WeatherLogic.skyColors(Cat.RAIN, night, dark = false).first
        assertEquals(WeatherLogic.mix(0xFFB3C1D3.toInt(), .45, 0xFFAEBBD4.toInt()), rainNight)
    }

    @Test fun temperatureColorsMatchTheWebApp() {
        assertEquals(0xFFE7C14A.toInt(), WeatherLogic.tempColor(65.0))
        assertEquals(0xFF5B6EE1.toInt(), WeatherLogic.tempColor(-10.0))
        assertEquals(0xFFE35A3A.toInt(), WeatherLogic.tempColor(110.0))
        // Halfway between 50° (71,184,160) and 65° (231,193,74)
        assertEquals(0xFF97BD75.toInt(), WeatherLogic.tempColor(57.5))
    }

    @Test fun rainAmountText() {
        assertNull(WeatherLogic.precipText(null))
        assertNull(WeatherLogic.precipText(0.004))
        assertEquals("0.08 in", WeatherLogic.precipText(0.08))
        assertEquals("1.25 in", WeatherLogic.precipText(1.25))
    }

    @Test fun roundingMatchesJavaScript() {
        assertEquals(3, WeatherLogic.jsRound(2.5))
        assertEquals(-2, WeatherLogic.jsRound(-2.5))
        assertEquals(71, WeatherLogic.jsRound(71.2))
    }

    @Test fun placeLabels() {
        assertEquals("Salem, OR", Place.DEFAULT.shortLabel)
        assertEquals("Paris, France", Place("Paris", "Île-de-France", "France", "FR", 48.85, 2.35).shortLabel)
        assertEquals("My location", Place("My location", "", "", "US", 44.9, -123.0).shortLabel)
        assertEquals("Salem, OR", Place.fromJson("""{"name":"Salem","region":"Oregon","country":"United States","cc":"US","lat":44.9429,"lon":-123.0351}""")!!.shortLabel)
        assertNull(Place.fromJson("not json"))
    }

    @Test fun textFollowsWallpaperOnlyWhenSeeThrough() {
        assertEquals(WidgetTheme.LIGHT, WidgetTheme.pick(phoneDark = false, transparency = 35, wallpaperSupportsDarkText = false))
        assertEquals(WidgetTheme.DARK, WidgetTheme.pick(phoneDark = true, transparency = 0, wallpaperSupportsDarkText = true))
        assertEquals(WidgetTheme.ON_DARK_WALLPAPER, WidgetTheme.pick(phoneDark = false, transparency = 40, wallpaperSupportsDarkText = false))
        assertEquals(WidgetTheme.LIGHT, WidgetTheme.pick(phoneDark = true, transparency = 80, wallpaperSupportsDarkText = true))
    }

    @Test fun sevenDaysOnlyWhenWide() {
        assertFalse(WidgetRenderer.isSevenDay(310f))
        assertTrue(WidgetRenderer.isSevenDay(380f))
    }
}
