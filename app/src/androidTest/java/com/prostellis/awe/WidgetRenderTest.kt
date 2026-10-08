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
import com.prostellis.awe.widget.ButtonSpot
import com.prostellis.awe.widget.Rendered
import com.prostellis.awe.widget.WidgetKind
import com.prostellis.awe.widget.WidgetRenderer
import com.prostellis.awe.widget.WidgetTheme
import com.prostellis.awe.widget.WidgetUpdater
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
