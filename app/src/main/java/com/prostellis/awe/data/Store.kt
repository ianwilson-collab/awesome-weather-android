package com.prostellis.awe.data

import android.content.Context
import com.prostellis.awe.weather.Forecast
import com.prostellis.awe.weather.Place
import java.net.HttpURLConnection
import java.net.URL

/** How often a widget refreshes on its own. */
enum class RefreshMode(val minutes: Int?) {
    EVERY_30(30), EVERY_60(60), MANUAL(null);
}

/** Settings for one widget on the home screen. */
data class WidgetSettings(val transparency: Int, val refresh: RefreshMode, val showCurrent: Boolean) {
    companion object {
        val DEFAULT = WidgetSettings(transparency = 0, refresh = RefreshMode.EVERY_30, showCurrent = true)
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
    )

    fun saveWidgetSettings(id: Int, s: WidgetSettings) {
        prefs.edit()
            .putInt("w$id.transparency", s.transparency)
            .putString("w$id.refresh", s.refresh.name)
            .putBoolean("w$id.showCurrent", s.showCurrent)
            .apply()
    }

    /** When this widget last showed newly downloaded data. */
    fun widgetUpdatedAt(id: Int): Long = prefs.getLong("w$id.updated", 0)

    fun markWidgetUpdated(id: Int, at: Long) {
        prefs.edit().putLong("w$id.updated", at).apply()
    }

    fun forgetWidget(id: Int) {
        prefs.edit().remove("w$id.transparency").remove("w$id.refresh").remove("w$id.showCurrent").remove("w$id.updated").apply()
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
    }
}
