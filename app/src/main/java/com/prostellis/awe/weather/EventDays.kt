package com.prostellis.awe.weather

import org.json.JSONObject

/**
 * Event days from the web app (holidays, seasons, meteor showers, eclipses, auroras, new and full moons):
 * the color of each day's first sticker, which the forecast widget shows as a small dot under the day name.
 */
object EventDays {
    /** `{"2026-10-21":"#8a6cff", …}` → date to ARGB color. Bad entries are skipped. */
    fun parse(json: String): Map<String, Int> = runCatching {
        val o = JSONObject(json)
        o.keys().asSequence().mapNotNull { day ->
            val hex = o.optString(day).removePrefix("#")
            if (hex.length == 6) hex.toLongOrNull(16)?.let { day to (0xFF000000 or it).toInt() } else null
        }.toMap()
    }.getOrDefault(emptyMap())
}
