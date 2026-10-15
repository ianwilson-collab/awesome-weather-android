package com.prostellis.awe.data

import android.content.Context
import com.prostellis.awe.weather.EventDays
import com.prostellis.awe.weather.Forecast
import com.prostellis.awe.weather.Place
import com.prostellis.awe.widget.BgStyle
import com.prostellis.awe.widget.ClockColor
import com.prostellis.awe.widget.ClockSettings
import com.prostellis.awe.widget.DatePos
import com.prostellis.awe.widget.IconStyle
import com.prostellis.awe.widget.TimeFormat
import java.net.HttpURLConnection
import java.net.URL

/** How often a widget refreshes on its own. */
enum class RefreshMode(val minutes: Int?) {
    EVERY_30(30), EVERY_60(60), MANUAL(null);
}

/**
 * Settings for one widget on the home screen. [tempColor] is a [com.prostellis.awe.widget.TempColors] choice (0 = Auto);
 * [glow] is a [com.prostellis.awe.widget.Glow] step for the halo behind the temperatures; [showRain] shows the forecast widget's rain amounts.
 */
data class WidgetSettings(val transparency: Int, val refresh: RefreshMode, val showCurrent: Boolean, val tempColor: Int, val glow: Int, val showRain: Boolean) {
    companion object {
        val DEFAULT = WidgetSettings(transparency = 0, refresh = RefreshMode.EVERY_30, showCurrent = true, tempColor = 0, glow = com.prostellis.awe.widget.Glow.DEFAULT, showRain = true)
    }
}

/** A forecast as last downloaded, with when and for which place. */
data class SavedForecast(val forecast: Forecast, val fetchedAt: Long, val placeKey: String)

/** Everything the app and widgets keep on the phone. */
class Store(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("awe", Context.MODE_PRIVATE)

    /** The place saved in the app; the web app's default until one is picked. */
    val place: Place
        get() = prefs.getString(KEY_PLACE, null)?.let(Place::fromJson) ?: Place.DEFAULT

    /** Saves the app's place (raw JSON from the page). Returns true when it's a different place than before. */
    fun savePlaceJson(json: String): Boolean {
        val next = Place.fromJson(json) ?: return false
        val before = place
        prefs.edit().putString(KEY_PLACE, json).apply()
        return next.key != before.key || next.shortLabel != before.shortLabel
    }

    /** The weather-symbol style, shared by the app and every widget (Glow until one is picked). */
    var iconStyle: IconStyle
        get() = IconStyle.fromId(prefs.getString(KEY_ICON_STYLE, null))
        set(value) { prefs.edit().putString(KEY_ICON_STYLE, value.id).apply() }

    /** The app's background style, which the widget sky follows (Dynamic until one is picked). */
    val bgStyle: BgStyle
        get() = BgStyle.fromId(prefs.getString(KEY_BG_STYLE, null))

    /** Saves the background style picked in the app. Returns true when it changed. */
    fun saveBgStyle(style: BgStyle): Boolean {
        if (style == bgStyle) return false
        prefs.edit().putString(KEY_BG_STYLE, style.id).apply()
        return true
    }

    /** Event days from the app, about 13 months ahead: date → sticker color. */
    val eventColors: Map<String, Int>
        get() = prefs.getString(KEY_EVENTS, null)?.let(EventDays::parse) ?: emptyMap()

    /** Saves the event days the app worked out (JSON from the page). Returns true when they changed. */
    fun saveEventsJson(json: String): Boolean {
        if (EventDays.parse(json).isEmpty() || json == prefs.getString(KEY_EVENTS, null)) return false
        prefs.edit().putString(KEY_EVENTS, json).apply()
        return true
    }

    fun savedForecast(): SavedForecast? {
        val json = prefs.getString(KEY_FORECAST, null) ?: return null
        val forecast = runCatching { Forecast.parse(json) }.getOrNull() ?: return null
        return SavedForecast(forecast, prefs.getLong(KEY_FETCHED_AT, 0), prefs.getString(KEY_FORECAST_PLACE, "") ?: "")
    }

    /** The saved forecast, only if it belongs to the current place. */
    fun forecastForCurrentPlace(): SavedForecast? = savedForecast()?.takeIf { it.placeKey == place.key }

    /** Downloads a fresh forecast for the current place. Returns true on success; the old forecast is kept on failure. */
    fun refreshForecast(): Boolean {
        val p = place
        val json = runCatching { download(Forecast.url(p)) }.getOrNull() ?: return false
        if (runCatching { Forecast.parse(json) }.isFailure) return false
        prefs.edit()
            .putString(KEY_FORECAST, json)
            .putLong(KEY_FETCHED_AT, System.currentTimeMillis())
            .putString(KEY_FORECAST_PLACE, p.key)
            .apply()
        return true
    }

    fun widgetSettings(id: Int) = WidgetSettings(
        transparency = prefs.getInt("w$id.transparency", WidgetSettings.DEFAULT.transparency),
        refresh = prefs.getString("w$id.refresh", null)
            ?.let { runCatching { RefreshMode.valueOf(it) }.getOrNull() } ?: WidgetSettings.DEFAULT.refresh,
        showCurrent = prefs.getBoolean("w$id.showCurrent", WidgetSettings.DEFAULT.showCurrent),
        tempColor = prefs.getInt("w$id.tempColor", WidgetSettings.DEFAULT.tempColor),
        glow = prefs.getInt("w$id.glow", WidgetSettings.DEFAULT.glow),
        showRain = prefs.getBoolean("w$id.showRain", WidgetSettings.DEFAULT.showRain),
    )

    fun saveWidgetSettings(id: Int, s: WidgetSettings) {
        prefs.edit()
            .putInt("w$id.transparency", s.transparency)
            .putString("w$id.refresh", s.refresh.name)
            .putBoolean("w$id.showCurrent", s.showCurrent)
            .putInt("w$id.tempColor", s.tempColor)
            .putInt("w$id.glow", s.glow)
            .putBoolean("w$id.showRain", s.showRain)
            .apply()
    }

    fun clockSettings(id: Int): ClockSettings {
        val d = ClockSettings()
        val k = "w$id.clock."
        return ClockSettings(
            face = prefs.getString("${k}face", d.face) ?: d.face,
            format = prefs.getString("${k}format", null)?.let { runCatching { TimeFormat.valueOf(it) }.getOrNull() } ?: d.format,
            timeColor = ClockColor.decode(prefs.getString("${k}timeColor", null)),
            glow = prefs.getInt("${k}glow", d.glow),
            transparency = prefs.getInt("${k}transparency", d.transparency),
            weather = prefs.getBoolean("${k}weather", d.weather),
            hiLo = prefs.getBoolean("${k}hiLo", d.hiLo),
            tempColor = ClockColor.decode(prefs.getString("${k}tempColor", null)),
            date = prefs.getBoolean("${k}date", d.date),
            datePos = prefs.getString("${k}datePos", null)?.let { runCatching { DatePos.valueOf(it) }.getOrNull() } ?: d.datePos,
            dateColor = ClockColor.decode(prefs.getString("${k}dateColor", null)),
        )
    }

    fun saveClockSettings(id: Int, s: ClockSettings) {
        val k = "w$id.clock."
        prefs.edit()
            .putString("${k}face", s.face)
            .putString("${k}format", s.format.name)
            .putString("${k}timeColor", s.timeColor.encode())
            .putInt("${k}glow", s.glow)
            .putInt("${k}transparency", s.transparency)
            .putBoolean("${k}weather", s.weather)
            .putBoolean("${k}hiLo", s.hiLo)
            .putString("${k}tempColor", s.tempColor.encode())
            .putBoolean("${k}date", s.date)
            .putString("${k}datePos", s.datePos.name)
            .putString("${k}dateColor", s.dateColor.encode())
            .apply()
    }

    /** When this widget last showed newly downloaded data. */
    fun widgetUpdatedAt(id: Int): Long = prefs.getLong("w$id.updated", 0)

    fun markWidgetUpdated(id: Int, at: Long) {
        prefs.edit().putLong("w$id.updated", at).apply()
    }

    /** Removes everything saved for a widget that's been taken off the home screen. */
    fun forgetWidget(id: Int) {
        val edit = prefs.edit()
        prefs.all.keys.filter { it.startsWith("w$id.") }.forEach { edit.remove(it) }
        edit.apply()
    }

    private fun download(url: String): String {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.connectTimeout = 8000
        conn.readTimeout = 8000
        try {
            if (conn.responseCode != 200) error("HTTP ${conn.responseCode}")
            return conn.inputStream.bufferedReader().use { it.readText() }
        } finally {
            conn.disconnect()
        }
    }

    private companion object {
        const val KEY_PLACE = "place"
        const val KEY_FORECAST = "forecast"
        const val KEY_FETCHED_AT = "fetchedAt"
        const val KEY_FORECAST_PLACE = "forecastPlace"
        const val KEY_ICON_STYLE = "iconStyle"
        const val KEY_BG_STYLE = "bgStyle"
        const val KEY_EVENTS = "events"
    }
}
