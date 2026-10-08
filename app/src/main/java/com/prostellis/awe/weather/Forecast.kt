package com.prostellis.awe.weather

import org.json.JSONArray
import org.json.JSONObject

/** A saved place, in the same shape the web app stores under localStorage "wx7.loc". */
data class Place(
    val name: String,
    val region: String,
    val country: String,
    val cc: String,
    val lat: Double,
    val lon: Double,
) {
    /** "Salem, OR" for U.S. places, "Paris, France" elsewhere (matches the web app's placeShort). */
    val shortLabel: String
        get() {
            val second = if (cc.equals("US", ignoreCase = true)) US_STATE_ABBR[region] ?: region else country
            return listOf(name, second).filter { it.isNotBlank() }.joinToString(", ")
        }

    /** Same rounding the web app uses to tell places apart. */
    val key: String get() = "%.3f,%.3f".format(java.util.Locale.US, lat, lon)

    companion object {
        val DEFAULT = Place("Salem", "Oregon", "United States", "US", 44.9429, -123.0351)

        fun fromJson(json: String): Place? = runCatching {
            val o = JSONObject(json)
            Place(
                name = o.optString("name"),
                region = o.optString("region"),
                country = o.optString("country"),
                cc = o.optString("cc"),
                lat = o.getDouble("lat"),
                lon = o.getDouble("lon"),
            )
        }.getOrNull()

        private val US_STATE_ABBR = mapOf(
            "Alabama" to "AL", "Alaska" to "AK", "Arizona" to "AZ", "Arkansas" to "AR", "California" to "CA",
            "Colorado" to "CO", "Connecticut" to "CT", "Delaware" to "DE", "District of Columbia" to "DC",
            "Florida" to "FL", "Georgia" to "GA", "Hawaii" to "HI", "Idaho" to "ID", "Illinois" to "IL",
            "Indiana" to "IN", "Iowa" to "IA", "Kansas" to "KS", "Kentucky" to "KY", "Louisiana" to "LA",
            "Maine" to "ME", "Maryland" to "MD", "Massachusetts" to "MA", "Michigan" to "MI", "Minnesota" to "MN",
            "Mississippi" to "MS", "Missouri" to "MO", "Montana" to "MT", "Nebraska" to "NE", "Nevada" to "NV",
            "New Hampshire" to "NH", "New Jersey" to "NJ", "New Mexico" to "NM", "New York" to "NY",
            "North Carolina" to "NC", "North Dakota" to "ND", "Ohio" to "OH", "Oklahoma" to "OK", "Oregon" to "OR",
            "Pennsylvania" to "PA", "Rhode Island" to "RI", "South Carolina" to "SC", "South Dakota" to "SD",
            "Tennessee" to "TN", "Texas" to "TX", "Utah" to "UT", "Vermont" to "VT", "Virginia" to "VA",
            "Washington" to "WA", "West Virginia" to "WV", "Wisconsin" to "WI", "Wyoming" to "WY",
            "Puerto Rico" to "PR", "Guam" to "GU", "U.S. Virgin Islands" to "VI",
        )
    }
}

data class Current(val time: String, val temp: Double, val code: Int, val isDay: Boolean)

data class Daily(
    val time: List<String>,
    val code: List<Int?>,
    val max: List<Double?>,
    val min: List<Double?>,
    val precip: List<Double?>,
    val sunrise: List<String>,
    val sunset: List<String>,
)

data class Hourly(val time: List<String>, val temp: List<Double?>, val code: List<Int?>)

/** The parts of an Open-Meteo forecast response the widgets use. */
data class Forecast(
    val utcOffsetSeconds: Int,
    val current: Current,
    val daily: Daily,
    val hourly: Hourly,
) {
    companion object {
        /** Request fields: a subset of what the web app asks for, plus 8 days so day 7 has its overnight low. */
        fun url(place: Place): String =
            "https://api.open-meteo.com/v1/forecast?latitude=${place.lat}&longitude=${place.lon}" +
                "&current=temperature_2m,weather_code,is_day" +
                "&daily=weather_code,temperature_2m_max,temperature_2m_min,precipitation_sum,sunrise,sunset" +
                "&hourly=temperature_2m,weather_code" +
                "&temperature_unit=fahrenheit&precipitation_unit=inch&timezone=auto&forecast_days=8"

        fun parse(json: String): Forecast {
            val o = JSONObject(json)
            val c = o.getJSONObject("current")
            val d = o.getJSONObject("daily")
            val h = o.getJSONObject("hourly")
            return Forecast(
                utcOffsetSeconds = o.optInt("utc_offset_seconds", 0),
                current = Current(
                    time = c.getString("time"),
                    temp = c.getDouble("temperature_2m"),
                    code = c.getInt("weather_code"),
                    isDay = c.optInt("is_day", 1) == 1,
                ),
                daily = Daily(
                    time = d.getJSONArray("time").strings(),
                    code = d.getJSONArray("weather_code").ints(),
                    max = d.getJSONArray("temperature_2m_max").doubles(),
                    min = d.getJSONArray("temperature_2m_min").doubles(),
                    precip = d.getJSONArray("precipitation_sum").doubles(),
                    sunrise = d.getJSONArray("sunrise").strings(),
                    sunset = d.getJSONArray("sunset").strings(),
                ),
                hourly = Hourly(
                    time = h.getJSONArray("time").strings(),
                    temp = h.getJSONArray("temperature_2m").doubles(),
                    code = h.getJSONArray("weather_code").ints(),
                ),
            )
        }

        private fun JSONArray.strings() = List(length()) { getString(it) }
        private fun JSONArray.doubles() = List(length()) { if (isNull(it)) null else getDouble(it) }
        private fun JSONArray.ints() = List(length()) { if (isNull(it)) null else getInt(it) }
    }
}
