package com.prostellis.awe.data

import java.net.HttpURLConnection
import java.net.URL

/** Downloads through the phone's own connection: used by the widgets, the background refresh and the app's page. */
object Net {
    private const val USER_AGENT = "AWE Weather (github.com/ianwilson-collab/awesome-weather-android)"

    fun get(url: String, accept: String = "application/json", timeoutMs: Int = 20_000): String {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.connectTimeout = minOf(timeoutMs, 15_000)
        conn.readTimeout = timeoutMs
        conn.setRequestProperty("Accept", accept)
        conn.setRequestProperty("User-Agent", USER_AGENT)
        try {
            if (conn.responseCode !in 200..299) error("HTTP ${conn.responseCode}")
            return conn.inputStream.bufferedReader().use { it.readText() }
        } finally {
            conn.disconnect()
        }
    }
}
