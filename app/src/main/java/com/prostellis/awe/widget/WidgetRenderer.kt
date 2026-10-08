package com.prostellis.awe.widget

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.text.TextPaint
import android.text.TextUtils
import com.prostellis.awe.R
import com.prostellis.awe.weather.Cat
import com.prostellis.awe.weather.Daylight
import com.prostellis.awe.weather.WeatherLogic
import com.prostellis.awe.weather.WidgetModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/** Where a tap target sits on the widget, in dp from the top-left corner. */
data class ButtonSpot(val centerX: Float, val centerY: Float, val iconSize: Float)

/** A drawn widget plus where the refresh and settings buttons go on top of it. */
class Rendered(val bitmap: Bitmap, val refresh: ButtonSpot, val gear: ButtonSpot, val buttonColor: Int)

enum class WidgetKind { SMALL, FORECAST }

/**
 * Draws the widgets exactly as approved in the mock-ups. Everything is laid out in dp and drawn
 * into a bitmap at the phone's density; the two buttons are separate views placed on top.
 */
class WidgetRenderer(private val context: Context) {

    private val density = context.resources.displayMetrics.density
    private val cornerDp = context.resources.getDimension(android.R.dimen.system_app_widget_background_radius) / density
    private val font: Typeface = context.resources.getFont(R.font.dm_sans)
    private val timeFormat = SimpleDateFormat("h:mm a", Locale.US)
    private val dayFormat = SimpleDateFormat("EEE", Locale.US)

    fun render(
        kind: WidgetKind,
        model: WidgetModel?,
        widthDp: Float,
        heightDp: Float,
        transparency: Int,
        theme: WidgetTheme,
        skyDark: Boolean,
    ): Rendered {
        val w = max(widthDp, 40f)
        val h = max(heightDp, 40f)
        val bitmap = Bitmap.createBitmap((w * density).toInt(), (h * density).toInt(), Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.scale(density, density)
        val bgAlpha = 1f - transparency / 100f
        drawSky(canvas, w, h, model?.nowCat ?: Cat.PARTLY, model?.light ?: Daylight(1.0, false, true), skyDark, bgAlpha)
        return when (kind) {
            WidgetKind.SMALL -> drawSmall(canvas, bitmap, w, h, model, theme)
            WidgetKind.FORECAST -> drawForecast(canvas, bitmap, w, h, model, theme, transparency)
        }
    }

    /* ---------------- Background ---------------- */

    private fun drawSky(canvas: Canvas, w: Float, h: Float, cat: Cat, light: Daylight, dark: Boolean, alpha: Float) {
        if (alpha <= 0f) return
        val rect = RectF(0f, 0f, w, h)
        val (top, bottom) = WeatherLogic.skyColors(cat, light, dark)
        // 165° CSS gradient: from the top, leaning slightly left-to-right
        val rad = Math.toRadians(165.0 - 90.0)
        val len = (w * cos(rad) + h * sin(rad)).toFloat() / 2f
        val cx = w / 2f; val cy = h / 2f
        val dx = (cos(rad) * len).toFloat(); val dy = (sin(rad) * len).toFloat()
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        paint.shader = LinearGradient(cx - dx, cy - dy, cx + dx, cy + dy, top, bottom, Shader.TileMode.CLAMP)
        paint.alpha = (255 * alpha).toInt()
        canvas.drawRoundRect(rect, cornerDp, cornerDp, paint)

        canvas.save()
        val clip = Path().apply { addRoundRect(rect, cornerDp, cornerDp, Path.Direction.CW) }
        canvas.clipPath(clip)
        if (cat.isOpenSky && light.d > 0) {
            val sun = WeatherLogic.sunColor(cat, light, dark)
            glow(canvas, w * .92f, 0f, 125f, sun, (.53f * alpha * light.d.toFloat()))
        }
        if (cat.isOpenSky && light.d < 1) {
            val night = (1 - light.d).toFloat() * alpha
            glow(canvas, w * .95f, 0f, 105f, 0xFFFDF2C9.toInt(), .35f * night)
            val star = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = if (dark) 0xFFF4F8FF.toInt() else 0xFFFFFFFF.toInt() }
            star.alpha = (255 * night).toInt()
            for ((fx, fy) in STARS) canvas.drawCircle(w * fx, h * fy, 1.1f, star)
        }
        canvas.restore()
    }

    private fun glow(canvas: Canvas, x: Float, y: Float, r: Float, color: Int, strength: Float) {
        if (strength <= 0f) return
        val a = (255 * strength.coerceIn(0f, 1f)).toInt()
        val c0 = (a shl 24) or (color and 0xFFFFFF)
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        p.shader = RadialGradient(x, y, r, intArrayOf(c0, color and 0xFFFFFF), floatArrayOf(0f, .7f), Shader.TileMode.CLAMP)
        canvas.drawCircle(x, y, r, p)
    }

    /* ---------------- Small (2×1) ---------------- */

    private fun drawSmall(canvas: Canvas, bitmap: Bitmap, w: Float, h: Float, m: WidgetModel?, t: WidgetTheme): Rendered {
        val icon = 40f
        val top = (h - 62f) / 2f
        val refresh = ButtonSpot(w - 8f - 15f - 7f - 7.5f, 8f + 7.5f, 15f)
        val gear = ButtonSpot(w - 8f - 7.5f, 8f + 7.5f, 15f)
        if (m == null) {
            text(canvas, "Loading forecast…", 12f, h / 2f + 4f, 12f, 500, t.ink2, t)
            return Rendered(bitmap, refresh, gear, t.ink2)
        }
        WeatherIcons.draw(canvas, m.nowCat, m.light.isDay, 12f, (h - icon) / 2f, icon, t.icons, t.textShadow)
        val x = 12f + icon + 8f
        // Shrinks a little for temperatures like 105° or -12° so it never runs into the refresh button
        val tempText = "${m.temp}°"
        val room = refresh.centerX - refresh.iconSize / 2 - 4f - x
        val tempSize = min(26f, 26f * room / measure(tempText, 26f, 600))
        text(canvas, tempText, x, top + 24f, tempSize, 600, t.ink, t)
        val textW = w - x - 8f
        text(canvas, fit(m.nowText, 12f, 500, textW), x, top + 43f, 12f, 500, t.ink2, t)
        val line3 = "${m.place.name} · ${timeFormat.format(Date(m.fetchedAt))}"
        text(canvas, fit(line3, 10f, 400, textW), x, top + 58f, 10f, 400, t.ink3, t)
        return Rendered(bitmap, refresh, gear, t.ink2)
    }

    /* ---------------- Forecast (4×2, 7 days when wide) ---------------- */

    private fun drawForecast(canvas: Canvas, bitmap: Bitmap, w: Float, h: Float, m: WidgetModel?, t: WidgetTheme, transparency: Int): Rendered {
        val sevenDay = isSevenDay(w)
        val compact = sevenDay
        val pad = 8f
        val bandH = if (compact) 40f else 52f
        val band = RectF(pad, pad, w - pad, pad + bandH)
        val panelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = t.panel
            alpha = (((t.panel ushr 24) and 0xFF) * (1f - transparency * .7f / 100f)).toInt()
        }
        canvas.drawRoundRect(band, 15f, 15f, panelPaint)

        // Right side of the band: [AWE] ↻ ⚙ above the city
        val toolsY = if (compact) band.top + 3f + 9f else band.top + 5f + 9f
        val gear = ButtonSpot(band.right - 10f - 9f, toolsY, 18f)
        val refresh = ButtonSpot(gear.centerX - 9f - 11f - 9f, toolsY, 18f)
        if (m == null) {
            text(canvas, "Loading forecast…", band.left + 12f, band.centerY() + 4f, 12.5f, 600, t.ink, t)
            return Rendered(bitmap, refresh, gear, t.ink2)
        }
        val city = m.place.shortLabel
        val cityW = measure(city, 11.5f, 600)
        val toolsLeft = refresh.centerX - 9f
        val aweW = measure("AWE", 11f, 800) + .6f * 2
        val awePossibleLeft = toolsLeft - 11f - aweW

        // Left side: icon, temperature, then condition and details
        val iconSize = if (compact) 30f else 38f
        WeatherIcons.draw(canvas, m.nowCat, m.light.isDay, band.left + 7f, band.centerY() - iconSize / 2, iconSize, t.icons, t.textShadow)
        val tempSize = if (compact) 26f else 30f
        val tempText = "${m.temp}°"
        val tempX = band.left + 7f + iconSize + 8f
        text(canvas, tempText, tempX, band.centerY() + tempSize * .36f, tempSize, 600, t.ink, t, letterSpacingDp = -1f)
        val midX = tempX + measure(tempText, tempSize, 600, -1f) + 8f
        val updated = "Updated ${timeFormat.format(Date(m.fetchedAt))}"
        val feels = "Feels like ${m.feels}°"
        val subLine = if (compact) "$feels · $updated" else null

        // The AWE mark is always on the 7-day widget; on 5 days only when the details still fit beside it.
        val rightWithAwe = min(awePossibleLeft, band.right - 10f - cityW) - 8f
        val rightWithout = min(toolsLeft, band.right - 10f - cityW) - 8f
        val widest = maxOf(
            measure(m.nowText, 12.5f, 600),
            if (compact) measure(subLine!!, 10.5f, 400) else max(measure(feels, 10.5f, 400), measure(updated, 9.5f, 400)),
        )
        val showAwe = sevenDay || widest <= rightWithAwe - midX
        val midW = (if (showAwe) rightWithAwe else rightWithout) - midX
        if (showAwe) drawAwe(canvas, awePossibleLeft, toolsY + 4f, 11f, t)

        if (compact) {
            text(canvas, fit(m.nowText, 12.5f, 600, midW), midX, band.centerY() - 2f, 12.5f, 600, t.ink, t)
            drawFeelsUpdated(canvas, feels, updated, midX, band.centerY() + 12f, midW, t)
            text(canvas, city, band.right - 10f - cityW, band.bottom - 6f, 11.5f, 600, t.ink, t)
        } else {
            text(canvas, fit(m.nowText, 12.5f, 600, midW), midX, band.top + 17f, 12.5f, 600, t.ink, t)
            text(canvas, fit(feels, 10.5f, 400, midW), midX, band.top + 31f, 10.5f, 400, t.ink2, t)
            text(canvas, fit(updated, 9.5f, 400, midW), midX, band.top + 44f, 9.5f, 400, t.ink3, t)
            text(canvas, city, band.right - 10f - cityW, band.bottom - 8f, 11.5f, 600, t.ink, t)
        }

        drawDays(canvas, RectF(pad, band.bottom + 6f, w - pad, h - pad), m.days.take(if (sevenDay) 7 else 5), t)
        return Rendered(bitmap, refresh, gear, t.ink2)
    }

    private fun drawFeelsUpdated(canvas: Canvas, feels: String, updated: String, x: Float, y: Float, maxW: Float, t: WidgetTheme) {
        val dot = " · "
        val feelsW = measure(feels, 10.5f, 400)
        val dotW = measure(dot, 10.5f, 400)
        if (feelsW + dotW + measure(updated, 10.5f, 400) <= maxW) {
            text(canvas, feels, x, y, 10.5f, 400, t.ink2, t)
            text(canvas, dot, x + feelsW, y, 10.5f, 400, t.ink3, t)
            text(canvas, updated, x + feelsW + dotW, y, 10.5f, 400, t.ink3, t)
        } else {
            text(canvas, fit(feels, 10.5f, 400, maxW), x, y, 10.5f, 400, t.ink2, t)
        }
    }

    private fun drawAwe(canvas: Canvas, x: Float, baseline: Float, size: Float, t: WidgetTheme) {
        var cx = x
        for ((ch, color) in listOf("A" to t.awe.first, "W" to t.awe.second, "E" to t.awe.third)) {
            text(canvas, ch, cx, baseline, size, 800, color, t, letterSpacingDp = .6f)
            cx += measure(ch, size, 800, .6f)
        }
    }

    private fun drawDays(canvas: Canvas, area: RectF, days: List<com.prostellis.awe.weather.Day>, t: WidgetTheme) {
        if (days.isEmpty()) return
        val gap = 2f
        val colW = (area.width() - gap * (days.size - 1)) / days.size
        val his = days.mapNotNull { it.hi }; val los = days.mapNotNull { it.lo }
        val wMax = (his + los).maxOrNull() ?: 0.0
        val wMin = (his + los).minOrNull() ?: 0.0
        val nameH = 13f; val iconH = 21f; val amtH = 12f
        val tempsTop = area.top + 3f + nameH + iconH
        val tempsH = area.bottom - 2f - amtH - tempsTop
        val lab = 13f
        val travel = tempsH - 2 * lab - 2f
        fun y(v: Double) = tempsTop + lab + 1f + (if (wMax > wMin) ((wMax - v) / (wMax - wMin)).toFloat() * travel else travel / 2)

        days.forEachIndexed { i, d ->
            val left = area.left + i * (colW + gap)
            val col = RectF(left, area.top, left + colW, area.bottom)
            val cx = col.centerX()
            if (i == 0) canvas.drawRoundRect(col, 12f, 12f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = t.today })

            val name = if (i == 0) "Today" else dayFormat.format(java.sql.Date.valueOf(d.ymd))
            centered(canvas, name, cx, area.top + 3f + 10.5f, 11.5f, 600, if (i == 0) t.todayName else t.ink, t)
            WeatherIcons.draw(canvas, d.cat, true, cx - 10f, area.top + 3f + nameH + 1f, 20f, t.icons, t.textShadow)

            if (WeatherLogic.showsRainBar(d.pop)) {
                val bh = max(4f, (d.pop!! / 100.0).toFloat() * tempsH)
                val r = RectF(col.left + 7f, tempsTop + tempsH - bh, col.right - 7f, tempsTop + tempsH)
                val p = Paint(Paint.ANTI_ALIAS_FLAG)
                val rgb = t.rainBlock and 0xFFFFFF
                p.shader = LinearGradient(0f, r.top, 0f, r.bottom, (0x6B shl 24) or rgb, (0x29 shl 24) or rgb, Shader.TileMode.CLAMP)
                canvas.drawPath(Path().apply {
                    addRoundRect(r, floatArrayOf(4f, 4f, 4f, 4f, 2f, 2f, 2f, 2f), Path.Direction.CW)
                }, p)
            }
            if (d.hi != null && d.lo != null) {
                val top = y(d.hi); val bottom = y(d.lo)
                val bar = Paint(Paint.ANTI_ALIAS_FLAG)
                bar.shader = LinearGradient(0f, top, 0f, max(bottom, top + 1f),
                    WeatherLogic.tempColor(d.hi), WeatherLogic.tempColor(d.lo), Shader.TileMode.CLAMP)
                canvas.drawRoundRect(RectF(cx - 2.5f, top, cx + 2.5f, max(bottom, top + 5f)), 3f, 3f, bar)
                centered(canvas, "${WeatherLogic.jsRound(d.hi)}°", cx, top - 2.5f, 12f, 600, t.ink, t)
                centered(canvas, "${WeatherLogic.jsRound(d.lo)}°", cx, max(bottom, top + 5f) + 10.5f, 12f, 500, t.ink2, t)
            }
            WeatherLogic.precipText(d.precip)?.let {
                centered(canvas, it, cx, area.bottom - 2f - 2.5f, 9.5f, 600, t.amount, t)
            }
        }
    }

    /* ---------------- Text ---------------- */

    private fun paint(sizeDp: Float, weight: Int, color: Int, t: WidgetTheme?, letterSpacingDp: Float = 0f) =
        TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = font
            fontVariationSettings = "'wght' $weight"
            textSize = sizeDp
            this.color = color
            letterSpacing = letterSpacingDp / sizeDp
            if (t?.textShadow == true) setShadowLayer(2f, 0f, 1f, 0x73000000)
        }

    private fun text(canvas: Canvas, s: String, x: Float, baseline: Float, size: Float, weight: Int, color: Int, t: WidgetTheme, letterSpacingDp: Float = 0f) {
        canvas.drawText(s, x, baseline, paint(size, weight, color, t, letterSpacingDp))
    }

    private fun centered(canvas: Canvas, s: String, cx: Float, baseline: Float, size: Float, weight: Int, color: Int, t: WidgetTheme) {
        val p = paint(size, weight, color, t)
        canvas.drawText(s, cx - p.measureText(s) / 2f, baseline, p)
    }

    private fun measure(s: String, size: Float, weight: Int, letterSpacingDp: Float = 0f) =
        paint(size, weight, 0, null, letterSpacingDp).measureText(s)

    private fun fit(s: String, size: Float, weight: Int, maxW: Float): String =
        TextUtils.ellipsize(s, paint(size, weight, 0, null), max(0f, maxW), TextUtils.TruncateAt.END).toString()

    companion object {
        /** Wide enough for 7 days: a 5-column-wide widget, or a full-width one. */
        const val SEVEN_DAY_MIN_WIDTH_DP = 340f

        fun isSevenDay(widthDp: Float) = widthDp >= SEVEN_DAY_MIN_WIDTH_DP

        /** Star spots near the edges, clear of the text. */
        private val STARS = listOf(.04f to .06f, .52f to .03f, .97f to .52f, .02f to .60f, .98f to .88f)
    }
}
