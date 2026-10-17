package com.prostellis.awe

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import android.view.LayoutInflater
import android.widget.FrameLayout
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.prostellis.awe.weather.Forecast
import com.prostellis.awe.weather.Place
import com.prostellis.awe.weather.WeatherLogic
import com.prostellis.awe.weather.WidgetModel
import com.prostellis.awe.widget.BgStyle
import com.prostellis.awe.widget.ClockColor
import com.prostellis.awe.widget.ClockFace
import com.prostellis.awe.widget.ClockRenderer
import com.prostellis.awe.widget.ClockRendered
import com.prostellis.awe.widget.ClockSettings
import com.prostellis.awe.widget.ClockUpdater
import com.prostellis.awe.widget.ColorFamily
import com.prostellis.awe.widget.DatePos
import com.prostellis.awe.widget.IconStyle
import com.prostellis.awe.widget.Look
import com.prostellis.awe.widget.TimeFormat
import com.prostellis.awe.widget.WidgetSky
import com.prostellis.awe.widget.WidgetTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.time.Instant

/** Draws the clock widget's sizes, styles, colors and options from a sample Salem forecast, and saves the pictures for review. */
@RunWith(AndroidJUnit4::class)
class ClockRenderTest {

    private val context: Context = InstrumentationRegistry.getInstrumentation().targetContext
    private val testAssets = InstrumentationRegistry.getInstrumentation().context.assets
    private val forecast = Forecast.parse(testAssets.open("forecast_salem.json").bufferedReader().readText())
    init { java.util.TimeZone.setDefault(java.util.TimeZone.getTimeZone("America/Los_Angeles")) }

    private val renderer = ClockRenderer(context)
    private val out = File(context.getExternalFilesDir(null), "renders").apply { mkdirs() }
    private val afternoon = Instant.parse("2026-10-08T16:27:00Z").toEpochMilli() + 7 * 3600_000L
    private val model: WidgetModel = WeatherLogic.model(Place.DEFAULT, forecast, afternoon, afternoon, 7)
    private val sizes = listOf(2 to 152f, 3 to 233f, 4 to 310f, 5 to 388f)

    private fun look(dark: Boolean, style: BgStyle = BgStyle.DYNAMIC, transparency: Int = 0, glow: Int = 2): Look {
        val sky = WidgetSky.picture(context, style, model.nowCat, model.light, dark, "2026-10-08")
        return Look(transparency, WidgetTheme.pick(dark, transparency, false, sky?.isDark), dark, IconStyle.THREE_D, sky = sky, glow = glow)
    }

    private fun draw(s: ClockSettings, w: Float, dark: Boolean = false, style: BgStyle = BgStyle.DYNAMIC, m: WidgetModel? = model) =
        renderer.render(s, m, w, 74f, look(dark, style, s.transparency, s.glow), afternoon, phone24 = false)

    /** Every size, light and dark, with the defaults (weather on), High and low, and the date above and below. */
    @Test fun drawsEverySizeAndOption() {
        for ((cols, w) in sizes) for (dark in listOf(false, true)) {
            val tag = "${cols}x1-${if (dark) "dark" else "light"}"
            save("clock-$tag", draw(ClockSettings(), w, dark))
            save("clock-$tag-hilo", draw(ClockSettings(hiLo = true), w, dark))
            save("clock-$tag-date-below", draw(ClockSettings(date = true), w, dark))
            save("clock-$tag-date-above-hilo", draw(ClockSettings(date = true, datePos = DatePos.ABOVE, hiLo = true), w, dark))
            save("clock-$tag-no-weather", draw(ClockSettings(weather = false), w, dark))
        }
    }

    @Test fun drawsEveryClockStyle() {
        for (face in ClockFace.ALL) {
            save("clock-style-${face.id}", draw(ClockSettings(face = face.id, date = true), 310f))
            save("clock-style-${face.id}-2x1", draw(ClockSettings(face = face.id), 152f, dark = true))
            save("clock-style-${face.id}-24h", draw(ClockSettings(face = face.id, format = TimeFormat.H24, weather = false), 152f))
        }
    }

    /** Solid, custom, metallic and gradient colors for the time, date and temperature, and every glow strength. */
    @Test fun drawsColorsAndGlow() {
        val metal = ClockColor(ColorFamily.METAL, 4)
        val sunset = ClockColor(ColorFamily.GRAD, 11)
        val orange = ClockColor(ColorFamily.SOLID, 6)
        val custom = ClockColor(ColorFamily.CUSTOM, hex = 0xFFFFC200.toInt())
        save("clock-colors-metal-grad", draw(ClockSettings(timeColor = metal, tempColor = sunset, dateColor = orange, date = true, hiLo = true), 310f))
        save("clock-colors-custom", draw(ClockSettings(timeColor = custom, date = true, dateColor = custom), 388f, dark = true))
        for (i in 0 until 12) {
            save("clock-metal-$i", draw(ClockSettings(timeColor = ClockColor(ColorFamily.METAL, i)), 233f, dark = i % 2 == 1))
            save("clock-grad-$i", draw(ClockSettings(timeColor = ClockColor(ColorFamily.GRAD, i)), 233f, dark = i % 2 == 0))
        }
        for (glow in 0..4) save("clock-glow-$glow", draw(ClockSettings(glow = glow, timeColor = ClockColor(ColorFamily.METAL, 0)), 310f, style = BgStyle.MT_HOOD))
    }

    /** The weather line in every arrangement, on the 4×1 and 2×1, and in the date's color. */
    @Test fun drawsTheWeatherLine() {
        val combos = listOf(
            "below" to ClockSettings(weather = false, wxLine = true),
            "above" to ClockSettings(weather = false, wxLine = true, wxPos = DatePos.ABOVE),
            "same-below" to ClockSettings(weather = false, wxLine = true, date = true),
            "same-above" to ClockSettings(weather = false, wxLine = true, date = true, datePos = DatePos.ABOVE),
            "under-date" to ClockSettings(weather = false, wxLine = true, date = true, wxSame = false),
            "over-date" to ClockSettings(weather = false, wxLine = true, date = true, wxSame = false, wxOrder = DatePos.ABOVE),
            "both-above" to ClockSettings(weather = false, wxLine = true, date = true, datePos = DatePos.ABOVE, wxSame = false, wxPos = DatePos.ABOVE),
            "split" to ClockSettings(weather = false, wxLine = true, date = true, datePos = DatePos.ABOVE, wxSame = false),
            "with-right" to ClockSettings(wxLine = true, date = true),
            "heat" to ClockSettings(weather = false, wxLine = true, date = true, wxSame = false, dateColor = ClockColor(ColorFamily.GRAD, 0)),
        )
        for ((tag, s) in combos) {
            save("clock-wxline-$tag", draw(s, 310f))
            save("clock-wxline-$tag-2x1", draw(s, 152f, dark = true))
        }
    }

    @Test fun drawsTransparencyAndEveryBackgroundStyle() {
        for (t in listOf(0, 50, 100)) save("clock-transparent-$t", draw(ClockSettings(transparency = t, date = true), 310f), wallpaper = true)
        for (style in BgStyle.entries) save("clock-sky-${style.id}", draw(ClockSettings(date = true), 310f, style = style))
    }

    /** The time area opens the Clock app and the weather area opens AWE; with the weather off the time takes the whole widget. */
    @Test fun tapAreasFollowTheWeatherBlock() {
        val on = draw(ClockSettings(), 310f)
        val weather = on.weatherArea
        assertNotNull(weather)
        assertEquals(on.timeArea.right, weather!!.left, .01f)
        assertEquals(310f, weather.right, .01f)
        assertTrue("weather block stays narrow", weather.width() < 80f)
        // High and low never widens the block
        assertEquals(weather.width(), draw(ClockSettings(hiLo = true), 310f).weatherArea!!.width(), .01f)
        val off = draw(ClockSettings(weather = false), 310f)
        assertNull(off.weatherArea)
        assertEquals(310f, off.timeArea.width(), .01f)
        // Before the first download there's no weather block yet
        assertNull(draw(ClockSettings(), 310f, m = null).weatherArea)
        val d = context.resources.displayMetrics.density
        assertEquals((310 * d).toInt(), on.bitmap.width)
        assertEquals((74 * d).toInt(), on.bitmap.height)
    }

    /** The exact views the home screen gets must load, and the settings screen must inflate. */
    @Test fun homeScreenViewsAndSettingsLoad() {
        for (s in listOf(ClockSettings(), ClockSettings(weather = false))) {
            ClockUpdater.buildViews(context, draw(s, 310f)).apply(context, FrameLayout(context))
        }
        val themed = android.view.ContextThemeWrapper(context, R.style.Theme_Awe_WidgetSettings)
        LayoutInflater.from(themed).inflate(R.layout.activity_clock_settings, FrameLayout(themed), false)
        for (face in ClockFace.ALL) assertNotNull(face.typeface(context))
    }

    private fun save(name: String, r: ClockRendered, wallpaper: Boolean = false) {
        val bmp = Bitmap.createBitmap(r.bitmap.width, r.bitmap.height, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        if (wallpaper) {
            c.drawPaint(Paint().apply {
                shader = LinearGradient(0f, 0f, bmp.width * .4f, bmp.height.toFloat(), 0xFF5EA89A.toInt(), 0xFF0D1C26.toInt(), Shader.TileMode.CLAMP)
            })
        }
        c.drawBitmap(r.bitmap, 0f, 0f, null)
        File(out, "$name.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}
