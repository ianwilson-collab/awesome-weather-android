package com.prostellis.awe.weather

import java.util.Locale
import kotlin.math.roundToInt

/** Display categories, same as the web app's wmoCat(). */
enum class Cat { CLEAR, PARTLY, CLOUDY, FOG, DRIZZLE, RAIN, SNOW, STORM;
    val isPrecip get() = this == DRIZZLE || this == RAIN || this == SNOW || this == STORM
    val isOpenSky get() = this == CLEAR || this == PARTLY
}

/** One day column on the forecast widget. */
data class Day(val ymd: String, val cat: Cat, val hi: Double?, val lo: Double?, val pop: Double?, val precip: Double?)

/** How "day" the sky is right now: 0 = night … 1 = full day, and which twilight we're in. */
data class Daylight(val d: Double, val rising: Boolean, val isDay: Boolean)

/** Everything a widget draws, worked out from a forecast at a moment in time. */
data class WidgetModel(
    val place: Place,
    val nowCat: Cat,
    val nowText: String,
    val temp: Int,
    val feels: Int,
    val light: Daylight,
    val days: List<Day>,
    val fetchedAt: Long,
)

object WeatherLogic {

    fun wmoCat(code: Int?): Cat = when {
        code == null -> Cat.CLOUDY
        code <= 1 -> Cat.CLEAR
        code == 2 -> Cat.PARTLY
        code == 3 -> Cat.CLOUDY
        code == 45 || code == 48 -> Cat.FOG
        code in 51..57 -> Cat.DRIZZLE
        code in 61..67 || code in 80..82 -> Cat.RAIN
        code in 71..77 || code == 85 || code == 86 -> Cat.SNOW
        code >= 95 -> Cat.STORM
        else -> Cat.CLOUDY
    }

    private val WMO_TEXT = mapOf(
        0 to "Clear", 1 to "Mostly clear", 2 to "Partly cloudy", 3 to "Cloudy", 45 to "Fog", 48 to "Freezing fog",
        51 to "Light drizzle", 53 to "Drizzle", 55 to "Heavy drizzle", 56 to "Freezing drizzle", 57 to "Freezing drizzle",
        61 to "Light rain", 63 to "Rain", 65 to "Heavy rain", 66 to "Freezing rain", 67 to "Freezing rain",
        71 to "Light snow", 73 to "Snow", 75 to "Heavy snow", 77 to "Snow grains",
        80 to "Light showers", 81 to "Showers", 82 to "Heavy showers", 85 to "Snow showers", 86 to "Heavy snow showers",
        95 to "Thunderstorms", 96 to "Thunderstorms, hail", 99 to "Thunderstorms, hail",
    )

    fun wmoText(code: Int): String = WMO_TEXT[code] ?: "—"

    /** JavaScript Math.round, so temperatures match the web app exactly. */
    fun jsRound(v: Double): Int = kotlin.math.floor(v + 0.5).toInt()

    /** Location-local date for a moment, as "YYYY-MM-DD". */
    fun localYmd(nowMs: Long, utcOffsetSeconds: Int): String {
        val t = java.time.Instant.ofEpochMilli(nowMs).atOffset(java.time.ZoneOffset.ofTotalSeconds(utcOffsetSeconds))
        return t.toLocalDate().toString()
    }

    fun localMinutes(nowMs: Long, utcOffsetSeconds: Int): Int {
        val t = java.time.Instant.ofEpochMilli(nowMs).atOffset(java.time.ZoneOffset.ofTotalSeconds(utcOffsetSeconds))
        return t.hour * 60 + t.minute
    }

    /** What the day will mostly be: daytime hours (7am–7pm), precipitation counts a bit more. Same as the web app. */
    fun dominantCat(f: Forecast, ymd: String): Cat {
        val counts = LinkedHashMap<Cat, Double>()
        for (i in f.hourly.time.indices) {
            val t = f.hourly.time[i]
            if (!t.startsWith(ymd)) continue
            val h = t.substring(11, 13).toInt()
            if (h < 7 || h > 19) continue
            val c = wmoCat(f.hourly.code[i])
            counts[c] = (counts[c] ?: 0.0) + if (c.isPrecip) 1.6 else 1.0
        }
        if (counts.isEmpty()) return wmoCat(f.daily.code.getOrNull(f.daily.time.indexOf(ymd)))
        return counts.entries.sortedByDescending { it.value }.first().key
    }

    /** Overnight low: 6pm on the day through 8am the next morning (how NWS reports lows). Same as the web app. */
    fun overnightLow(f: Forecast, ymd: String): Double? {
        val start = f.hourly.time.indexOf("${ymd}T18:00")
        if (start < 0) return null
        val temps = (start until minOf(start + 15, f.hourly.time.size)).mapNotNull { f.hourly.temp[it] }
        return if (temps.size >= 10) temps.min() else null
    }

    fun buildDays(f: Forecast, nowMs: Long, count: Int): List<Day> {
        val today = localYmd(nowMs, f.utcOffsetSeconds)
        val start = f.daily.time.indexOf(today).takeIf { it >= 0 }
            ?: f.daily.time.indexOf(f.current.time.take(10)).coerceAtLeast(0)
        return (start until minOf(start + count, f.daily.time.size)).map { i ->
            val ymd = f.daily.time[i]
            Day(
                ymd = ymd,
                cat = dominantCat(f, ymd),
                hi = f.daily.max[i],
                lo = overnightLow(f, ymd) ?: f.daily.min[i],
                pop = f.daily.pop[i],
                precip = f.daily.precip[i],
            )
        }
    }

    private const val RISE_BEFORE = 60
    private const val RISE_AFTER = 15
    private const val SET_BEFORE = 15
    private const val SET_AFTER = 60

    fun daylight(f: Forecast, nowMs: Long): Daylight {
        val t = localMinutes(nowMs, f.utcOffsetSeconds)
        val i = f.daily.time.indexOf(localYmd(nowMs, f.utcOffsetSeconds))
        if (i < 0) return Daylight(if (f.current.isDay) 1.0 else 0.0, t < 720, f.current.isDay)
        fun mins(iso: String) = iso.substring(11, 13).toInt() * 60 + iso.substring(14, 16).toInt()
        val sr = mins(f.daily.sunrise[i])
        val ss = mins(f.daily.sunset[i])
        val isDay = t in sr until ss
        val riseStart = sr - RISE_BEFORE; val riseEnd = sr + RISE_AFTER
        val setStart = ss - SET_BEFORE; val setEnd = ss + SET_AFTER
        return when {
            t <= riseStart || t >= setEnd -> Daylight(0.0, t < 720, isDay)
            t < riseEnd -> Daylight((t - riseStart).toDouble() / (riseEnd - riseStart), true, isDay)
            t <= setStart -> Daylight(1.0, false, isDay)
            else -> Daylight(1 - (t - setStart).toDouble() / (setEnd - setStart), false, isDay)
        }
    }

    fun model(place: Place, f: Forecast, fetchedAt: Long, nowMs: Long, dayCount: Int): WidgetModel = WidgetModel(
        place = place,
        nowCat = wmoCat(f.current.code),
        nowText = wmoText(f.current.code),
        temp = jsRound(f.current.temp),
        feels = jsRound(f.current.feels),
        light = daylight(f, nowMs),
        days = buildDays(f, nowMs, dayCount),
        fetchedAt = fetchedAt,
    )

    /** "0.08 in" when there's measurable precipitation, otherwise nothing (the web app's threshold). */
    fun precipText(v: Double?): String? =
        if (v == null || v < 0.005) null else String.format(Locale.US, "%.2f in", v)

    /** Rain bar shows from a 10% chance up. */
    fun showsRainBar(pop: Double?): Boolean = pop != null && pop >= 10

    /* ---------- Colors (ARGB ints, worked out without Android so they can be unit-tested) ---------- */

    /** CSS color-mix(in srgb, a p, b): straight blend per channel. */
    fun mix(a: Int, p: Double, b: Int): Int {
        val q = p.coerceIn(0.0, 1.0)
        fun ch(shift: Int) = (((a shr shift) and 0xFF) * q + ((b shr shift) and 0xFF) * (1 - q)).roundToInt()
        return (0xFF shl 24) or (ch(16) shl 16) or (ch(8) shl 8) or ch(0)
    }

    private val TEMP_STOPS = listOf(
        15.0 to 0x5B6EE1, 35.0 to 0x4A9EDE, 50.0 to 0x47B8A0, 65.0 to 0xE7C14A, 80.0 to 0xF0913D, 95.0 to 0xE35A3A,
    )

    /** Cool → warm color for a temperature (°F), same stops as the web app. */
    fun tempColor(t: Double): Int {
        if (t <= TEMP_STOPS.first().first) return 0xFF000000.toInt() or TEMP_STOPS.first().second
        for (k in 1 until TEMP_STOPS.size) {
            val (t1, c1) = TEMP_STOPS[k]
            if (t <= t1) {
                val (t0, c0) = TEMP_STOPS[k - 1]
                return mix(0xFF000000.toInt() or c1, (t - t0) / (t1 - t0), 0xFF000000.toInt() or c0)
            }
        }
        return 0xFF000000.toInt() or TEMP_STOPS.last().second
    }

    /** Sky gradient colors: night → dawn/dusk → day, blended by daylight. Same formula as the web app. */
    fun skyColors(cat: Cat, light: Daylight, dark: Boolean): Pair<Int, Int> {
        val p = if (dark) SkyPalette.DARK else SkyPalette.LIGHT
        val tw = if (light.rising) p.dawn else p.dusk
        val d = light.d
        fun timeColor(n: Int) = if (d <= .5) mix(tw[n], d / .5, p.night[n])
            else mix(p.cat(cat)[n], (d - .5) / .5, tw[n])
        fun color(n: Int) = if (cat.isOpenSky) timeColor(n) else mix(p.cat(cat)[n], .45 + .55 * d, timeColor(n))
        return color(0) to color(1)
    }

    /** Sun glow color for clear/partly skies, warming toward dawn/dusk like the web app. */
    fun sunColor(cat: Cat, light: Daylight, dark: Boolean): Int {
        val p = if (dark) SkyPalette.DARK else SkyPalette.LIGHT
        val catSun = if (cat == Cat.CLEAR) p.clearSun else p.partlySun
        return mix(catSun, light.d, if (light.rising) p.dawnSun else p.duskSun)
    }
}

/** The web app's sky colors (CSS variables) for its light and dark themes. Index 0 = top, 1 = bottom. */
class SkyPalette private constructor(
    val night: IntArray, val dawn: IntArray, val dusk: IntArray,
    private val cats: Map<Cat, IntArray>,
    val dawnSun: Int, val duskSun: Int, val clearSun: Int, val partlySun: Int,
) {
    fun cat(c: Cat): IntArray = cats.getValue(c)

    companion object {
        private fun c(vararg rgb: Int) = IntArray(rgb.size) { 0xFF000000.toInt() or rgb[it] }
        private fun s(rgb: Int) = 0xFF000000.toInt() or rgb

        val LIGHT = SkyPalette(
            night = c(0xAEBBD4, 0xE3E8F0), dawn = c(0xF3C6BD, 0xFBE6CC), dusk = c(0xC8A6CF, 0xF8C99C),
            cats = mapOf(
                Cat.CLEAR to c(0xB9DCFF, 0xFFF3D1), Cat.PARTLY to c(0xC9DEF5, 0xF4F7FB),
                Cat.CLOUDY to c(0xD3DAE3, 0xEEF1F5), Cat.FOG to c(0xDDE1E6, 0xF3F4F6),
                Cat.DRIZZLE to c(0xCBD6E2, 0xEEF2F6), Cat.RAIN to c(0xB3C1D3, 0xE1E8EF),
                Cat.SNOW to c(0xD9E4F0, 0xF8FAFC), Cat.STORM to c(0x95A2B3, 0xD2D9E2),
            ),
            dawnSun = s(0xFFAD66), duskSun = s(0xFF9A52), clearSun = s(0xFFC23D), partlySun = s(0xFFCF5A),
        )

        val DARK = SkyPalette(
            night = c(0x0F1D33, 0x18263A), dawn = c(0x2F2C4D, 0x6A4950), dusk = c(0x2A2546, 0x6D4433),
            cats = mapOf(
                Cat.CLEAR to c(0x1D3E66, 0x3A4A5C), Cat.PARTLY to c(0x22384F, 0x2C3A4A),
                Cat.CLOUDY to c(0x26313D, 0x2F3A46), Cat.FOG to c(0x2A323B, 0x323B45),
                Cat.DRIZZLE to c(0x22303E, 0x2C3947), Cat.RAIN to c(0x1C2836, 0x273443),
                Cat.SNOW to c(0x26364A, 0x33445A), Cat.STORM to c(0x161E29, 0x232D3A),
            ),
            dawnSun = s(0xE8925A), duskSun = s(0xE8834A), clearSun = s(0xE8A93A), partlySun = s(0xD9A23E),
        )
    }
}
