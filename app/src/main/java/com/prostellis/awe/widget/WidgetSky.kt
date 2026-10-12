package com.prostellis.awe.widget

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.util.LruCache
import com.prostellis.awe.weather.Cat
import com.prostellis.awe.weather.Daylight

/** The app's background and animation style. [id] is the web app's name for it. */
enum class BgStyle(val id: String) {
    BASIC("default"), PAINTERLY("painterly"), DYNAMIC("realistic"), ILLUSTRATION("illustration"), MT_HOOD("mthood");

    /** The sky picture's file name: weather, time of day, and for Mt Hood the snow season. */
    fun pictureKey(cat: Cat, light: Daylight, ymd: String): String {
        val time = when {
            light.d >= .7 -> "day"
            light.d <= .15 -> "night"
            light.rising && this != MT_HOOD -> "dawn"
            else -> "dusk"
        }
        val base = "${cat.name.lowercase()}-$time"
        return if (this == MT_HOOD) "$base-${snowSeason(ymd.substring(5, 7).toInt())}" else base
    }

    companion object {
        /** People who never picked a style get Dynamic, as in the app. */
        fun fromId(id: String?): BgStyle = entries.firstOrNull { it.id == id } ?: DYNAMIC

        /** Mt Hood's snow: two-thirds of the mountain November to February, half March to May, the top third June to October. */
        fun snowSeason(month: Int): String = when (month) {
            11, 12, 1, 2 -> "twothirds"
            3, 4, 5 -> "half"
            else -> "third"
        }
    }
}

/** A still picture of the style's sky, and whether it's dark enough to need light text. */
class SkyPicture(val bitmap: Bitmap, val isDark: Boolean)

/**
 * The widget sky for every style but Basic: still pictures drawn from the app's own scenes by
 * tools/widget_skies.py and bundled under assets/widget-sky. Basic's sky is drawn by [WidgetRenderer].
 */
object WidgetSky {
    private val cache = LruCache<String, SkyPicture>(6)

    /** The picture for the weather and time of day, or null for Basic. [ymd] picks Mt Hood's snow line for the season. */
    fun picture(context: Context, style: BgStyle, cat: Cat, light: Daylight, dark: Boolean, ymd: String): SkyPicture? {
        if (style == BgStyle.BASIC) return null
        val path = "widget-sky/${style.id}/${style.pictureKey(cat, light, ymd)}-${if (dark) "dark" else "light"}.webp"
        cache.get(path)?.let { return it }
        val bitmap = runCatching { context.assets.open(path).use { BitmapFactory.decodeStream(it) } }.getOrNull() ?: return null
        return SkyPicture(bitmap, luminance(bitmap) < DARK_BELOW).also { cache.put(path, it) }
    }

    /** Average brightness (0 = black, 1 = white). */
    private fun luminance(b: Bitmap): Double {
        val c = Bitmap.createScaledBitmap(b, 1, 1, true).getPixel(0, 0)
        return (.2126 * Color.red(c) + .7152 * Color.green(c) + .0722 * Color.blue(c)) / 255
    }

    private const val DARK_BELOW = .45
}
