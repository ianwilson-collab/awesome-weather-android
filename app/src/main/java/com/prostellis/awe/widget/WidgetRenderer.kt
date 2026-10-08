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
        showCurrent: Boolean = true,
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
            WidgetKind.FORECAST -> drawForecast(canvas, bitmap, w, h, model, theme, transparency, showCurrent)
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
        // Grows with the widget: designed at 150 × 80 dp
        val s = min(h / 80f, w / 150f).coerceIn(.85f, 1.4f)
        val icon = 42f * s
        val tempSize = 28f * s; val condSize = 13f * s; val lineSize = 11f * s
        val blockH = tempSize * .95f + condSize * 1.3f + lineSize * 1.3f
        val top = (h - blockH) / 2f
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
        val fittedTemp = min(tempSize, tempSize * room / measure(tempText, tempSize, 600))
        text(canvas, tempText, x, top + tempSize * .9f, fittedTemp, 600, t.ink, t)
        val textW = w - x - 8f
        val condBase = top + tempSize * .95f + condSize * 1.15f
        text(canvas, fit(m.nowText, condSize, 600, textW), x, condBase, condSize, 600, t.ink2, t)
        val line3 = "${m.place.name} · ${timeFormat.format(Date(m.fetchedAt))}"
        text(canvas, fit(line3, lineSize, 400, textW), x, condBase + lineSize * 1.35f, lineSize, 400, t.ink3, t)
        return Rendered(bitmap, refresh, gear, t.ink2)
    }

    /* ---------------- Forecast (5 days, 7 when wide) ---------------- */

    /** Text and icon sizes for the forecast widget at a given scale (1 = the approved mock-up). */
    private class Sizes(s: Float) {
        val bandH = 50f * s; val bandIcon = 36f * s; val bandTemp = 34f * s
        val cond = 16.5f * s; val place = 13.5f * s; val updated = 11.5f
        val name = 15f * s; val icon = 28f * s; val hi = 18f * s; val lo = 16f * s; val amt = 12f * s
        val bar = 4.5f * s
    }

    private fun drawForecast(
        canvas: Canvas, bitmap: Bitmap, w: Float, h: Float, m: WidgetModel?, t: WidgetTheme,
        transparency: Int, showCurrent: Boolean,
    ): Rendered {
        val sevenDay = isSevenDay(w)
        val dayCount = if (sevenDay) 7 else 5
        val padX = 8f; val padTop = 8f; val padBottom = 5f; val gap = 6f
        val colW = (w - 2 * padX - 2f * (dayCount - 1)) / dayCount
        // Grows with the widget: designed at 204 dp tall with 53 dp columns
        val z = Sizes(min(h / 204f, colW / 53f).coerceIn(.8f, 1.35f))
        val bandH = if (showCurrent) z.bandH else 32f
        val band = RectF(padX, padTop, w - padX, padTop + bandH)
        val panel = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = t.panel
            alpha = (((t.panel ushr 24) and 0xFF) * (1f - transparency * .7f / 100f)).toInt()
        }
        canvas.drawRoundRect(band, 15f, 15f, panel)

        // Right side of the band: [AWE] ↻ ⚙, with the update time under them when current weather shows
        val toolsY = if (showCurrent) band.top + (bandH - 37f) / 2f + 9f else band.centerY()
        val gear = ButtonSpot(band.right - 10f - 9f, toolsY, 18f)
        val refresh = ButtonSpot(gear.centerX - 9f - 11f - 9f, toolsY, 18f)
        if (m == null) {
            text(canvas, "Loading forecast…", band.left + 12f, band.centerY() + 5f, 14f, 600, t.ink, t)
            return Rendered(bitmap, refresh, gear, t.ink2)
        }
        val aweW = measure("AWE", 12f, 800, .6f)
        val aweLeft = refresh.centerX - 9f - 11f - 2f - aweW
        val aweBaseline = toolsY + 4.3f

        if (showCurrent) {
            val updated = "Updated ${timeFormat.format(Date(m.fetchedAt))}"
            val updatedW = measure(updated, z.updated, 400)
            text(canvas, updated, band.right - 10f - updatedW, band.top + (bandH - 37f) / 2f + 18f + 6f + 10.5f, z.updated, 400, t.ink3, t)

            WeatherIcons.draw(canvas, m.nowCat, m.light.isDay, band.left + 9f, band.centerY() - z.bandIcon / 2, z.bandIcon, t.icons, t.textShadow)
            val tempText = "${m.temp}°"
            val tempX = band.left + 9f + z.bandIcon + 9f
            text(canvas, tempText, tempX, band.centerY() + z.bandTemp * .36f, z.bandTemp, 600, t.ink, t, letterSpacingDp = -1f)
            val midX = tempX + measure(tempText, z.bandTemp, 600, -1f) + 9f
            val place = m.place.shortLabel
            val widest = max(measure(m.nowText, z.cond, 600), measure(place, z.place, 600))
            // AWE always shows on the 7-day widget; on 5 days only when the details still fit beside it
            val withAwe = min(aweLeft, band.right - 10f - updatedW) - 8f - midX
            val without = min(refresh.centerX - 9f, band.right - 10f - updatedW) - 8f - midX
            val showAwe = sevenDay || widest <= withAwe
            if (showAwe) drawAwe(canvas, aweLeft, aweBaseline, 12f, t)
            val midW = if (showAwe) withAwe else without
            text(canvas, fit(m.nowText, z.cond, 600, midW), midX, band.centerY() - 2f, z.cond, 600, t.ink, t)
            text(canvas, fit(place, z.place, 600, midW), midX, band.centerY() + z.place + 1f, z.place, 600, t.ink2, t)
        } else {
            drawAwe(canvas, aweLeft, aweBaseline, 12f, t)
            text(canvas, fit(m.place.shortLabel, 14.5f, 600, aweLeft - 8f - band.left - 12f), band.left + 12f, band.centerY() + 5f, 14.5f, 600, t.ink, t)
        }

        drawDays(canvas, RectF(padX, band.bottom + gap, w - padX, h - padBottom), m.days.take(dayCount), z, t)
        return Rendered(bitmap, refresh, gear, t.ink2)
    }

    private fun drawAwe(canvas: Canvas, x: Float, baseline: Float, size: Float, t: WidgetTheme) {
        var cx = x
        for ((ch, color) in listOf("A" to t.awe.first, "W" to t.awe.second, "E" to t.awe.third)) {
            text(canvas, ch, cx, baseline, size, 800, color, t, letterSpacingDp = .6f)
            cx += measure(ch, size, 800, .6f)
        }
    }

    private fun drawDays(canvas: Canvas, area: RectF, days: List<com.prostellis.awe.weather.Day>, z: Sizes, t: WidgetTheme) {
        if (days.isEmpty()) return
        val gap = 2f
        val colW = (area.width() - gap * (days.size - 1)) / days.size
        val temps = days.flatMap { listOfNotNull(it.hi, it.lo) }
        val wMax = temps.maxOrNull() ?: 0.0
        val wMin = temps.minOrNull() ?: 0.0
        val colTop = 4f; val amtBottom = 3f
        val nameH = z.name * 1.15f; val iconH = z.icon + 1f; val amtH = z.amt * 1.1f
        val tempsTop = area.top + colTop + nameH + iconH
        val tempsH = area.bottom - amtBottom - amtH - 4f - tempsTop
        val labHi = z.hi * 1.08f; val labLo = z.lo * 1.08f
        val minBar = 12f
        val travel = max(minBar, tempsH - labHi - labLo - 6f)
        fun y(v: Double) = tempsTop + labHi + 3f +
            (if (wMax > wMin) ((wMax - v) / (wMax - wMin)).toFloat() else .5f) * (travel - minBar)

        days.forEachIndexed { i, d ->
            val left = area.left + i * (colW + gap)
            val col = RectF(left, area.top, left + colW, area.bottom)
            val cx = col.centerX()
            if (i == 0) canvas.drawRoundRect(col, 12f, 12f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = t.today })

            val name = if (i == 0) "Today" else dayFormat.format(java.sql.Date.valueOf(d.ymd))
            centered(canvas, name, cx, area.top + colTop + z.name * .95f, z.name, 600, if (i == 0) t.todayName else t.ink, t)
            WeatherIcons.draw(canvas, d.cat, true, cx - z.icon / 2, area.top + colTop + nameH, z.icon, t.icons, t.textShadow)

            if (d.hi != null && d.lo != null) {
                val top = y(d.hi)
                val bottom = y(d.lo) + minBar
                val bar = Paint(Paint.ANTI_ALIAS_FLAG)
                bar.shader = LinearGradient(0f, top, 0f, bottom, WeatherLogic.tempColor(d.hi), WeatherLogic.tempColor(d.lo), Shader.TileMode.CLAMP)
                canvas.drawRoundRect(RectF(cx - z.bar / 2, top, cx + z.bar / 2, bottom), z.bar, z.bar, bar)
                centered(canvas, "${WeatherLogic.jsRound(d.hi)}°", cx, top - 4f, z.hi, 700, t.ink, t)
                centered(canvas, "${WeatherLogic.jsRound(d.lo)}°", cx, bottom + 2f + z.lo * .92f, z.lo, 500, t.ink2, t)
            }

            // Rain amount in a soft blue pill, pinned to the bottom of the column
            WeatherLogic.precipText(d.precip)?.let { amount ->
                val tw = measure(amount, z.amt, 600)
                val pillH = z.amt * 1.3f
                val pill = RectF(cx - tw / 2 - 4f, area.bottom - amtBottom - pillH, cx + tw / 2 + 4f, area.bottom - amtBottom)
                canvas.drawRoundRect(pill, pillH / 2, pillH / 2, Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = (0x2E shl 24) or (t.rainBlock and 0xFFFFFF)
                })
                centered(canvas, amount, cx, pill.centerY() + z.amt * .35f, z.amt, 600, t.amount, t)
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
