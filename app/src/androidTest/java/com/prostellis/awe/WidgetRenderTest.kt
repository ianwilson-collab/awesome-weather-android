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
import com.prostellis.awe.weather.Forecast
import com.prostellis.awe.weather.Place
import com.prostellis.awe.weather.WeatherLogic
import com.prostellis.awe.weather.Cat
import com.prostellis.awe.widget.ButtonSpot
import com.prostellis.awe.widget.IconStyle
import com.prostellis.awe.widget.Rendered
import com.prostellis.awe.widget.WidgetKind
import com.prostellis.awe.widget.WidgetRenderer
import com.prostellis.awe.widget.WidgetTheme
import com.prostellis.awe.widget.WidgetUpdater
import com.prostellis.awe.widget.WeatherIcons
import org.junit.Assert.assertEquals
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

    @Test fun drawsAllSizesInLightAndDark() {
        for ((label, now) in listOf("day" to morning, "night" to evening)) {
            for (dark in listOf(false, true)) {
                val theme = if (dark) WidgetTheme.DARK else WidgetTheme.LIGHT
                val tag = "$label-${if (dark) "dark" else "light"}"
                save("small-$tag", renderer.render(WidgetKind.SMALL, model(now), 150f, 76f, 0, theme, dark), null)
                save("forecast-4x2-$tag", renderer.render(WidgetKind.FORECAST, model(now), 310f, 184f, 0, theme, dark), null)
                save("forecast-5x2-$tag", renderer.render(WidgetKind.FORECAST, model(now), 388f, 184f, 0, theme, dark), null)
            }
        }
    }

    /** Version 1.1 layout at the size of the widget on Ian's phone (full width, 204 dp tall) and at 4 cells on a 5-column screen. */
    @Test fun drawsBothWidthsWithAndWithoutCurrentWeather() {
        for (show in listOf(true, false)) {
            val tag = if (show) "current-on" else "current-off"
            save("v11-7day-$tag", renderer.render(WidgetKind.FORECAST, model(morning), 388f, 204f, 0, WidgetTheme.DARK, true, show), null)
            save("v11-5day-$tag", renderer.render(WidgetKind.FORECAST, model(morning), 310f, 204f, 0, WidgetTheme.DARK, true, show), null)
        }
        save("v11-7day-light-tall", renderer.render(WidgetKind.FORECAST, model(morning), 388f, 240f, 0, WidgetTheme.LIGHT, false), null)
        save("v11-small-tall", renderer.render(WidgetKind.SMALL, model(morning), 180f, 96f, 0, WidgetTheme.DARK, true), null)
    }

    /** Version 1.4: every icon style on the forecast and small widgets, light and dark. */
    @Test fun drawsEveryIconStyle() {
        for (style in IconStyle.entries) for (dark in listOf(false, true)) {
            val theme = if (dark) WidgetTheme.DARK else WidgetTheme.LIGHT
            val tag = "${style.id}-${if (dark) "dark" else "light"}"
            save("style-$tag-7day", renderer.render(WidgetKind.FORECAST, model(morning), 388f, 204f, 0, theme, dark, true, style), null)
            save("style-$tag-small", renderer.render(WidgetKind.SMALL, model(evening), 180f, 96f, 0, theme, dark, true, style), null)
        }
        val see = WidgetTheme.pick(phoneDark = false, transparency = 80, wallpaperSupportsDarkText = false)
        save("style-glow-transparent", renderer.render(WidgetKind.FORECAST, model(morning), 388f, 204f, 80, see, false, true, IconStyle.GLOW), wallpaper = true)
    }

    /** Version 1.5: event days get a small dot in the sticker color under the day name. */
    @Test fun marksEventDays() {
        val events = mapOf("2026-10-10" to 0xFF9AA6B4.toInt(), "2026-10-12" to 0xFFE8384F.toInt(), "2026-10-14" to 0xFF20B97F.toInt())
        for (dark in listOf(false, true)) {
            val theme = if (dark) WidgetTheme.DARK else WidgetTheme.LIGHT
            val tag = if (dark) "dark" else "light"
            save("events-$tag-7day", renderer.render(WidgetKind.FORECAST, model(morning), 388f, 204f, 0, theme, dark, true, IconStyle.DEFAULT, events), null)
            save("events-$tag-5day", renderer.render(WidgetKind.FORECAST, model(morning), 310f, 184f, 0, theme, dark, true, IconStyle.GLOW, events), null)
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
            val theme = WidgetTheme.pick(phoneDark = false, transparency = t, wallpaperSupportsDarkText = false)
            save("transparent-$t-forecast", renderer.render(WidgetKind.FORECAST, model(morning), 310f, 184f, t, theme, false), wallpaper = true)
            save("transparent-$t-small", renderer.render(WidgetKind.SMALL, model(morning), 150f, 76f, t, theme, false), wallpaper = true)
        }
    }

    @Test fun drawsExtremeTemperaturesWithoutOverlap() {
        for ((name, temp) in listOf("hot" to 105, "cold" to -12)) {
            val m = model(morning).copy(temp = temp)
            save("small-$name", renderer.render(WidgetKind.SMALL, m, 150f, 76f, 0, WidgetTheme.LIGHT, false), null)
            save("forecast-$name", renderer.render(WidgetKind.FORECAST, m, 310f, 184f, 0, WidgetTheme.LIGHT, false), null)
        }
    }

    @Test fun drawsLoadingStateBeforeTheFirstDownload() {
        save("forecast-loading", renderer.render(WidgetKind.FORECAST, null, 310f, 184f, 0, WidgetTheme.LIGHT, false), null)
        save("small-loading", renderer.render(WidgetKind.SMALL, null, 150f, 76f, 0, WidgetTheme.LIGHT, false), null)
    }

    @Test fun bitmapMatchesTheWidgetSize() {
        val r = renderer.render(WidgetKind.FORECAST, model(morning), 310f, 184f, 0, WidgetTheme.LIGHT, false)
        val d = context.resources.displayMetrics.density
        assertEquals((310 * d).toInt(), r.bitmap.width)
        assertEquals((184 * d).toInt(), r.bitmap.height)
        assertTrue(r.gear.centerX > r.refresh.centerX)
    }

    /** The exact views the home screen gets must load (catches anything a widget isn't allowed to use). */
    @Test fun homeScreenViewsLoad() {
        val r = renderer.render(WidgetKind.FORECAST, model(morning), 310f, 184f, 0, WidgetTheme.LIGHT, false)
        for (spinning in listOf(false, true)) {
            val views = WidgetUpdater.buildViews(context, 1, r, spinning)
            val parent = FrameLayout(context)
            views.apply(context, parent)
        }
    }

    @Test fun settingsScreenLoads() {
        val themed = android.view.ContextThemeWrapper(context, R.style.Theme_Awe_WidgetSettings)
        LayoutInflater.from(themed).inflate(R.layout.activity_widget_settings, FrameLayout(themed), false)
    }

    /** Saves the widget as the home screen would show it: picture, plus the two buttons on top. */
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
        drawButton(c, R.drawable.ic_gear, r.gear, r.buttonColor)
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
