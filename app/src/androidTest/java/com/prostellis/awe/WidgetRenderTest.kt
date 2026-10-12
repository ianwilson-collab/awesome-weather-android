package com.prostellis.awe

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.Shader
import android.view.LayoutInflater
import android.widget.FrameLayout
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.prostellis.awe.weather.Cat
import com.prostellis.awe.weather.Forecast
import com.prostellis.awe.weather.Place
import com.prostellis.awe.weather.WeatherLogic
import com.prostellis.awe.weather.WidgetModel
import com.prostellis.awe.widget.BgStyle
import com.prostellis.awe.widget.ButtonSpot
import com.prostellis.awe.widget.IconStyle
import com.prostellis.awe.widget.Look
import com.prostellis.awe.widget.Rendered
import com.prostellis.awe.widget.WeatherIcons
import com.prostellis.awe.widget.WidgetKind
import com.prostellis.awe.widget.WidgetRenderer
import com.prostellis.awe.widget.WidgetSky
import com.prostellis.awe.widget.WidgetTheme
import com.prostellis.awe.widget.WidgetUpdater
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.time.Instant

/**
 * Draws every widget variation from a sample Salem forecast on a real Android device and saves
 * the pictures (the build uploads them for review). Also checks the home-screen views load.
 */
@RunWith(AndroidJUnit4::class)
class WidgetRenderTest {

    private val context: Context = InstrumentationRegistry.getInstrumentation().targetContext
    private val testAssets = InstrumentationRegistry.getInstrumentation().context.assets
    private val forecast = Forecast.parse(testAssets.open("forecast_salem.json").bufferedReader().readText())
    init {
        // "Updated" times show in the phone's time zone; use Salem's so the pictures read naturally.
        java.util.TimeZone.setDefault(java.util.TimeZone.getTimeZone("America/Los_Angeles"))
    }

    private val renderer = WidgetRenderer(context)
    private val out = File(context.getExternalFilesDir(null), "renders").apply { mkdirs() }

    private fun at(local: String) = Instant.parse("$local:00Z").toEpochMilli() + 7 * 3600_000L
    private val morning = at("2026-10-08T09:35")
    private val evening = at("2026-10-08T21:40")
    private fun model(now: Long) = WeatherLogic.model(Place.DEFAULT, forecast, now, now, 7)
    private fun weather(now: Long, cat: Cat, text: String) = model(now).copy(nowCat = cat, nowText = text)

    /** Sizes on a 5-column home screen: 1×1, 2×1 … 5×1, 4×2 and 5×2. */
    private val tiny = 74f to 74f
    private val strips = listOf(2 to 152f, 3 to 233f, 4 to 310f, 5 to 388f)
    private val forecast4 = 310f to 184f
    private val forecast5 = 388f to 204f

    private fun look(
        kind: WidgetKind, m: WidgetModel?, dark: Boolean, style: BgStyle = BgStyle.BASIC, transparency: Int = 0,
        icons: IconStyle = IconStyle.GLOW, tempColor: Int = 0, wallpaperLight: Boolean = false, showCurrent: Boolean = true,
        ymd: String = "2026-10-08",
    ): Look {
        val sky = if (kind == WidgetKind.TINY || m == null) null else WidgetSky.picture(context, style, m.nowCat, m.light, dark, ymd)
        return Look(transparency, WidgetTheme.pick(dark, transparency, wallpaperLight, sky?.isDark), dark, icons, tempColor, sky, showCurrent, wallpaperLight)
    }

    private fun draw(kind: WidgetKind, m: WidgetModel?, size: Pair<Float, Float>, look: Look, events: Map<String, Int> = emptyMap()) =
        renderer.render(kind, m, size.first, size.second, look, events)

    /** Basic sky: every widget at every width, day and night, light and dark. */
    @Test fun drawsEveryWidgetAndWidth() {
        for ((label, now) in listOf("day" to morning, "night" to evening)) for (dark in listOf(false, true)) {
            val tag = "$label-${if (dark) "dark" else "light"}"
            val m = model(now)
            save("1x1-$tag", draw(WidgetKind.TINY, m, tiny, look(WidgetKind.TINY, m, dark)), null)
            for ((cols, w) in strips) {
                save("daily-${cols}x1-$tag", draw(WidgetKind.DAILY, m, w to 74f, look(WidgetKind.DAILY, m, dark)), null)
                save("hourly-${cols}x1-$tag", draw(WidgetKind.HOURLY, m, w to 74f, look(WidgetKind.HOURLY, m, dark)), null)
            }
            save("forecast-4x2-$tag", draw(WidgetKind.FORECAST, m, forecast4, look(WidgetKind.FORECAST, m, dark)), null)
            save("forecast-5x2-$tag", draw(WidgetKind.FORECAST, m, forecast5, look(WidgetKind.FORECAST, m, dark)), null)
        }
    }

    /** The widget sky follows the app's background style: every style on Daily 5×1, Hourly 4×1 and the forecast. */
    @Test fun drawsEveryBackgroundStyle() {
        for (style in BgStyle.entries) for ((label, now) in listOf("day" to morning, "night" to evening)) for (dark in listOf(false, true)) {
            val tag = "${style.id}-$label-${if (dark) "dark" else "light"}"
            val m = model(now)
            save("sky-$tag-daily", draw(WidgetKind.DAILY, m, 388f to 74f, look(WidgetKind.DAILY, m, dark, style)), null)
            save("sky-$tag-hourly", draw(WidgetKind.HOURLY, m, 310f to 74f, look(WidgetKind.HOURLY, m, dark, style)), null)
            save("sky-$tag-forecast", draw(WidgetKind.FORECAST, m, forecast5, look(WidgetKind.FORECAST, m, dark, style)), null)
        }
    }

    /** Mt Hood's snow line follows the season, and the weather changes the scene. */
    @Test fun drawsMtHoodThroughTheYear() {
        for ((name, ymd) in listOf("july" to "2026-07-15", "april" to "2026-04-15", "december" to "2026-12-10")) {
            val m = model(morning)
            save("hood-$name", draw(WidgetKind.FORECAST, m, forecast5, look(WidgetKind.FORECAST, m, false, BgStyle.MT_HOOD, ymd = ymd)), null)
        }
        for ((cat, text) in listOf(Cat.SNOW to "Snow", Cat.RAIN to "Rain", Cat.FOG to "Fog")) {
            val m = weather(morning, cat, text)
            save("hood-${cat.name.lowercase()}", draw(WidgetKind.DAILY, m, 388f to 74f, look(WidgetKind.DAILY, m, false, BgStyle.MT_HOOD, ymd = "2026-01-15")), null)
        }
    }

    /** Every style has a picture for every weather, time of day and theme (Mt Hood: every season too). */
    @Test fun everySkyPictureIsBundled() {
        val times = listOf(1.0 to false, .5 to true, .5 to false, 0.0 to false)
        for (style in BgStyle.entries) for (cat in Cat.entries) for ((d, rising) in times) for (dark in listOf(false, true)) {
            for (ymd in listOf("2026-01-15", "2026-04-15", "2026-07-15")) {
                val p = WidgetSky.picture(context, style, cat, com.prostellis.awe.weather.Daylight(d, rising, d > 0), dark, ymd)
                if (style == BgStyle.BASIC) assertNull(p) else assertNotNull("${style.id} $cat $d $rising $dark $ymd", p)
            }
        }
    }

    /** AWE 1×1: the weather tint for each kind of weather, day and night, and with picked temperature colors. */
    @Test fun drawsTheWeatherTint() {
        for ((cat, text) in listOf(Cat.CLEAR to "Clear", Cat.PARTLY to "Partly cloudy", Cat.RAIN to "Rain", Cat.SNOW to "Snow", Cat.STORM to "Thunderstorms", Cat.FOG to "Fog")) {
            for ((label, now) in listOf("day" to morning, "night" to evening)) {
                val m = weather(now, cat, text)
                save("1x1-${cat.name.lowercase()}-$label", draw(WidgetKind.TINY, m, tiny, look(WidgetKind.TINY, m, false)), null)
            }
        }
        for (color in listOf(5, 9, 4)) {
            val m = model(morning)
            save("1x1-color-$color", draw(WidgetKind.TINY, m, tiny, look(WidgetKind.TINY, m, false, tempColor = color)), null)
        }
    }

    /** Temperature color: every temperature on the widget takes it; lows and labels don't. */
    @Test fun drawsTemperatureColors() {
        for ((color, dark) in listOf(5 to true, 7 to false, 9 to true)) {
            val m = model(morning)
            val tag = "$color-${if (dark) "dark" else "light"}"
            save("tempcolor-$tag-daily", draw(WidgetKind.DAILY, m, 388f to 74f, look(WidgetKind.DAILY, m, dark, BgStyle.DYNAMIC, tempColor = color)), null)
            save("tempcolor-$tag-hourly", draw(WidgetKind.HOURLY, m, 310f to 74f, look(WidgetKind.HOURLY, m, dark, BgStyle.DYNAMIC, tempColor = color)), null)
            save("tempcolor-$tag-forecast", draw(WidgetKind.FORECAST, m, forecast5, look(WidgetKind.FORECAST, m, dark, BgStyle.DYNAMIC, tempColor = color)), null)
        }
    }

    /** Every icon style on the forecast and daily widgets, light and dark. */
    @Test fun drawsEveryIconStyle() {
        for (style in IconStyle.entries) for (dark in listOf(false, true)) {
            val tag = "${style.id}-${if (dark) "dark" else "light"}"
            save("style-$tag-7day", draw(WidgetKind.FORECAST, model(morning), forecast5, look(WidgetKind.FORECAST, model(morning), dark, icons = style)), null)
            save("style-$tag-daily", draw(WidgetKind.DAILY, model(evening), 388f to 74f, look(WidgetKind.DAILY, model(evening), dark, icons = style)), null)
        }
    }

    /** The forecast widget with the current weather turned off. */
    @Test fun drawsTheForecastWithoutCurrentWeather() {
        for ((name, size) in listOf("7day" to forecast5, "5day" to forecast4)) {
            save("forecast-$name-current-off", draw(WidgetKind.FORECAST, model(morning), size, look(WidgetKind.FORECAST, model(morning), true, showCurrent = false)), null)
        }
    }

    /** Event days get a small dot in the sticker color under the day name. */
    @Test fun marksEventDays() {
        val events = mapOf("2026-10-10" to 0xFF9AA6B4.toInt(), "2026-10-12" to 0xFFE8384F.toInt(), "2026-10-14" to 0xFF20B97F.toInt())
        for (dark in listOf(false, true)) {
            val tag = if (dark) "dark" else "light"
            save("events-$tag-7day", draw(WidgetKind.FORECAST, model(morning), forecast5, look(WidgetKind.FORECAST, model(morning), dark, icons = IconStyle.BASIC), events), null)
            save("events-$tag-5day", draw(WidgetKind.FORECAST, model(morning), forecast4, look(WidgetKind.FORECAST, model(morning), dark), events), null)
        }
    }

    /** Every symbol in every style, large, on light and dark rows (for checking against the approved mock-ups). */
    @Test fun drawsIconSheet() {
        val d = context.resources.displayMetrics.density
        val cell = 64f
        val symbols = listOf(Cat.CLEAR to true, Cat.CLEAR to false, Cat.PARTLY to true, Cat.PARTLY to false, Cat.CLOUDY to true,
            Cat.FOG to true, Cat.DRIZZLE to true, Cat.RAIN to true, Cat.SNOW to true, Cat.STORM to true)
        val rows = IconStyle.entries.flatMap { s -> listOf(s to WidgetTheme.LIGHT, s to WidgetTheme.DARK) }
        val bmp = Bitmap.createBitmap((symbols.size * cell * d).toInt(), (rows.size * cell * d).toInt(), Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        c.scale(d, d)
        rows.forEachIndexed { r, (style, theme) ->
            c.drawRect(0f, r * cell, symbols.size * cell, (r + 1) * cell, Paint().apply { color = if (theme.dark) 0xFF1C2632.toInt() else 0xFFF5F7FA.toInt() })
            symbols.forEachIndexed { i, (cat, day) -> WeatherIcons.draw(c, cat, day, i * cell + 8f, r * cell + 8f, cell - 16f, theme.icons, style) }
        }
        File(out, "icon-sheet.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Test fun drawsTransparencyOverAWallpaper() {
        for (t in listOf(0, 50, 80)) {
            val m = model(morning)
            save("transparent-$t-forecast", draw(WidgetKind.FORECAST, m, forecast4, look(WidgetKind.FORECAST, m, false, BgStyle.DYNAMIC, t)), wallpaper = true)
            save("transparent-$t-daily", draw(WidgetKind.DAILY, m, 310f to 74f, look(WidgetKind.DAILY, m, false, BgStyle.DYNAMIC, t)), wallpaper = true)
            save("transparent-$t-1x1", draw(WidgetKind.TINY, m, tiny, look(WidgetKind.TINY, m, false, transparency = t)), wallpaper = true)
        }
    }

    @Test fun drawsExtremeTemperaturesWithoutOverlap() {
        for ((name, temp) in listOf("hot" to 105, "cold" to -12)) {
            val m = model(morning).copy(temp = temp)
            save("daily-2x1-$name", draw(WidgetKind.DAILY, m, 152f to 74f, look(WidgetKind.DAILY, m, false)), null)
            save("hourly-2x1-$name", draw(WidgetKind.HOURLY, m, 152f to 74f, look(WidgetKind.HOURLY, m, false)), null)
            save("1x1-$name", draw(WidgetKind.TINY, m, tiny, look(WidgetKind.TINY, m, false)), null)
            save("forecast-$name", draw(WidgetKind.FORECAST, m, forecast4, look(WidgetKind.FORECAST, m, false)), null)
        }
    }

    @Test fun drawsLoadingStateBeforeTheFirstDownload() {
        for (kind in WidgetKind.entries) {
            val size = when (kind) { WidgetKind.TINY -> tiny; WidgetKind.FORECAST -> forecast4; else -> 233f to 74f }
            save("loading-${kind.name.lowercase()}", draw(kind, null, size, look(kind, null, false)), null)
        }
    }

    /** Every widget has a refresh button in the top-right; only the forecast widget has the settings button. */
    @Test fun everyWidgetHasRefreshOnlyTheForecastHasSettings() {
        val d = context.resources.displayMetrics.density
        val r = draw(WidgetKind.FORECAST, model(morning), forecast4, look(WidgetKind.FORECAST, model(morning), false))
        assertEquals((310 * d).toInt(), r.bitmap.width)
        assertEquals((184 * d).toInt(), r.bitmap.height)
        assertTrue(r.gear!!.centerX > r.refresh.centerX)
        for (kind in listOf(WidgetKind.TINY, WidgetKind.DAILY, WidgetKind.HOURLY)) {
            for (size in listOf(tiny, 152f to 74f, 388f to 74f)) {
                val other = draw(kind, model(morning), size, look(kind, model(morning), false))
                assertNull(other.gear)
                assertTrue("$kind refresh in the top-right", other.refresh.centerX > size.first * .75f && other.refresh.centerY < size.second / 3)
            }
        }
    }

    /** The exact views the home screen gets must load (catches anything a widget isn't allowed to use). */
    @Test fun homeScreenViewsLoad() {
        for (kind in WidgetKind.entries) {
            val r = draw(kind, model(morning), forecast4, look(kind, model(morning), false, BgStyle.MT_HOOD))
            for (spinning in listOf(false, true)) {
                val views = WidgetUpdater.buildViews(context, 1, r, spinning)
                views.apply(context, FrameLayout(context))
            }
        }
    }

    @Test fun settingsScreenLoads() {
        val themed = android.view.ContextThemeWrapper(context, R.style.Theme_Awe_WidgetSettings)
        LayoutInflater.from(themed).inflate(R.layout.activity_widget_settings, FrameLayout(themed), false)
    }

    /** Saves the widget as the home screen would show it: picture, plus its buttons on top. */
    private fun save(name: String, r: Rendered, wallpaper: Boolean?) {
        val bmp = Bitmap.createBitmap(r.bitmap.width, r.bitmap.height, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        if (wallpaper == true) {
            c.drawPaint(Paint().apply {
                shader = LinearGradient(0f, 0f, bmp.width * .4f, bmp.height.toFloat(), 0xFF5EA89A.toInt(), 0xFF0D1C26.toInt(), Shader.TileMode.CLAMP)
            })
        }
        c.drawBitmap(r.bitmap, 0f, 0f, null)
        drawButton(c, R.drawable.ic_refresh, r.refresh, r.buttonColor)
        r.gear?.let { drawButton(c, R.drawable.ic_gear, it, r.buttonColor) }
        File(out, "$name.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private fun drawButton(c: Canvas, res: Int, spot: ButtonSpot, color: Int) {
        val d = context.resources.displayMetrics.density
        val icon = context.getDrawable(res)!!.mutate()
        icon.colorFilter = PorterDuffColorFilter(color, PorterDuff.Mode.SRC_IN)
        val half = spot.iconSize / 2
        icon.setBounds(((spot.centerX - half) * d).toInt(), ((spot.centerY - half) * d).toInt(),
            ((spot.centerX + half) * d).toInt(), ((spot.centerY + half) * d).toInt())
        icon.draw(c)
    }
}
