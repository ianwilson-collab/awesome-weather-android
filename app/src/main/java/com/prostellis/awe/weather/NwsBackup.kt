package com.prostellis.awe.weather

import com.prostellis.awe.data.Net
import org.json.JSONArray
import org.json.JSONObject

/**
 * When Open-Meteo doesn't answer, the widgets fall back on the U.S. National Weather Service. Its forecast is turned into
 * the same shape as an Open-Meteo answer, so everything that draws the widgets works unchanged. Rain amounts aren't
 * included (the Weather Service doesn't give them), and sunrise and sunset come from the last saved forecast.
 */
object NwsBackup {
    private const val GEO = "application/geo+json"

    fun forecastJson(place: Place, saved: Forecast?): String? {
        if (!place.cc.equals("US", ignoreCase = true)) return null
        return runCatching {
            val lat = "%.4f".format(java.util.Locale.US, place.lat)
            val lon = "%.4f".format(java.util.Locale.US, place.lon)
            val pts = JSONObject(Net.get("https://api.weather.gov/points/$lat,$lon", GEO)).getJSONObject("properties")
            fromPeriods(Net.get(pts.getString("forecastHourly"), GEO), Net.get(pts.getString("forecast"), GEO), saved)
        }.getOrNull()
    }

    private fun periods(json: String): List<JSONObject> {
        val a = JSONObject(json).getJSONObject("properties").getJSONArray("periods")
        return List(a.length()) { a.getJSONObject(it) }
    }

    /** The hourly and 12-hour Weather Service forecasts → an Open-Meteo-shaped forecast. */
    fun fromPeriods(hourlyJson: String, forecastJson: String, saved: Forecast?): String {
        val hourly = periods(hourlyJson)
        val halves = periods(forecastJson)
        require(hourly.isNotEmpty())
        val first = hourly.first()
        val start = first.getString("startTime") // "2026-11-14T18:00:00-08:00"
        val offset = start.substring(19).let { (if (it[0] == '-') -1 else 1) * (it.substring(1, 3).toInt() * 3600 + it.substring(4, 6).toInt() * 60) }

        val hTime = JSONArray(); val hTemp = JSONArray(); val hCode = JSONArray()
        hourly.forEach { p ->
            hTime.put(p.getString("startTime").take(16)); hTemp.put(p.getDouble("temperature")); hCode.put(code(p.getString("shortForecast")))
        }

        // Days: highs and lows from the hours, then the 12-hour periods for days past the hourly forecast
        val days = linkedMapOf<String, DoubleArray>() // date -> [max, min]
        val dayCode = mutableMapOf<String, Int>()
        hourly.forEach { p ->
            val ymd = p.getString("startTime").take(10); val t = p.getDouble("temperature")
            val m = days.getOrPut(ymd) { doubleArrayOf(t, t) }
            m[0] = maxOf(m[0], t); m[1] = minOf(m[1], t)
        }
        // The 12-hour periods fill in the last hourly day (usually only partly covered) and every day after it
        val fullyHourly = days.keys.toList().dropLast(1).toSet()
        halves.forEach { p ->
            val ymd = p.getString("startTime").take(10); val t = p.getDouble("temperature")
            if (p.getBoolean("isDaytime")) dayCode[ymd] = code(p.getString("shortForecast"))
            if (ymd in fullyHourly) return@forEach
            val m = days.getOrPut(ymd) { doubleArrayOf(t, t) }
            m[0] = maxOf(m[0], t); m[1] = minOf(m[1], t)
        }

        val dTime = JSONArray(); val dCode = JSONArray(); val dMax = JSONArray(); val dMin = JSONArray(); val dPrecip = JSONArray()
        val rise = JSONArray(); val set = JSONArray()
        days.forEach { (ymd, m) ->
            dTime.put(ymd); dMax.put(m[0]); dMin.put(m[1]); dPrecip.put(JSONObject.NULL)
            dCode.put(dayCode[ymd] ?: hourly.firstOrNull { it.getString("startTime").startsWith("${ymd}T12") }?.let { code(it.getString("shortForecast")) } ?: 3)
            rise.put("${ymd}T${sunTime(saved, ymd, true)}"); set.put("${ymd}T${sunTime(saved, ymd, false)}")
        }

        return JSONObject()
            .put("utc_offset_seconds", offset)
            .put("current", JSONObject().put("time", start.take(16)).put("temperature_2m", first.getDouble("temperature"))
                .put("weather_code", code(first.getString("shortForecast"))).put("is_day", if (first.getBoolean("isDaytime")) 1 else 0))
            .put("daily", JSONObject().put("time", dTime).put("weather_code", dCode).put("temperature_2m_max", dMax)
                .put("temperature_2m_min", dMin).put("precipitation_sum", dPrecip).put("sunrise", rise).put("sunset", set))
            .put("hourly", JSONObject().put("time", hTime).put("temperature_2m", hTemp).put("weather_code", hCode))
            .toString()
    }

    /** That day's sunrise or sunset ("HH:MM") from the saved forecast, or its last day's when the date is past it. */
    private fun sunTime(saved: Forecast?, ymd: String, rise: Boolean): String {
        val list = (if (rise) saved?.daily?.sunrise else saved?.daily?.sunset).orEmpty()
        val hit = list.firstOrNull { it.startsWith(ymd) } ?: list.lastOrNull()
        return hit?.substring(11, 16) ?: if (rise) "07:00" else "17:00"
    }

    /** The Weather Service's wording → the nearest Open-Meteo (WMO) weather code. */
    fun code(text: String): Int {
        val t = text.lowercase()
        return when {
            "thunder" in t -> 95
            "freezing" in t || "sleet" in t || "ice" in t -> 66
            "snow" in t || "flurr" in t || "blizzard" in t -> if ("heavy" in t) 75 else if ("light" in t || "chance" in t) 71 else 73
            "drizzle" in t -> 51
            "shower" in t -> 80
            "rain" in t -> if ("heavy" in t) 65 else if ("light" in t || "chance" in t || "slight" in t) 61 else 63
            "fog" in t || "haze" in t || "smoke" in t -> 45
            "partly" in t -> 2
            "mostly sunny" in t || "mostly clear" in t -> 1
            "sunny" in t || "clear" in t -> 0
            "cloudy" in t || "overcast" in t -> 3
            else -> 3
        }
    }
}
