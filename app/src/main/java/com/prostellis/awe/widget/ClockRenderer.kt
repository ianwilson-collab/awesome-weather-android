package com.prostellis.awe.widget

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import android.text.TextPaint
import com.prostellis.awe.R
import com.prostellis.awe.weather.WeatherLogic
import com.prostellis.awe.weather.WidgetModel
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.max

/** A drawn clock, plus the areas a tap opens the Clock app ([timeArea]) or AWE ([weatherArea]), in dp. */
class ClockRendered(val bitmap: Bitmap, val timeArea: RectF, val weatherArea: RectF?)

/**
 * Draws the clock widget as approved on the mock-up board: hours and minutes as large as the row allows,
 * an optional day and date and an optional weather line above or below, and an optional weather block on the right drawn like the 1×1.
 * Laid out in dp at a 74 dp row and scaled with the row's height.
 */
class ClockRenderer(private val context: Context) {

    private val density = context.resources.displayMetrics.density
    private val base = WidgetRenderer(context)
    private val ui: Typeface = context.resources.getFont(R.font.dm_sans)

    fun render(
        settings: ClockSettings, model: WidgetModel?, widthDp: Float, heightDp: Float, look: Look,
        now: Long, phone24: Boolean,
    ): ClockRendered {
        val w = max(widthDp, 40f)
        val h = max(heightDp, 40f)
        val bitmap = Bitmap.createBitmap((w * density).toInt(), (h * density).toInt(), Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.scale(density, density)
        base.drawBackground(canvas, false, w, h, model, look)
        canvas.clipPath(base.rounded(w, h))
        val t = look.theme
        val s = (h / 74f).coerceIn(.6f, 1.6f)
        val e = 3f * s

        // Weather on the right, like the 1×1: the temperature over a big, faint icon, in a narrow block.
        // The block's width comes from the temperature alone; High and low never moves or widens anything.
        var right = w
        var weatherArea: RectF? = null
        if (settings.weather && model != null) {
            val temp = "${model.temp}°"
            val tempSize = 30f * s
            val bw = measure(ui, temp, tempSize, 700) + 10f * s
            val l = w - bw - 4f * s
            val cx = l + bw / 2
            val mid = h / 2
            val ic = h * .95f
            canvas.saveLayerAlpha(RectF(l, 0f, w, h), (255 * .4f).toInt())
            canvas.clipRect(l, 0f, w, h)
            WeatherIcons.draw(canvas, model.nowCat, model.light.isDay, cx - ic / 2, mid - ic / 2, ic, t.icons, look.iconStyle)
            canvas.restore()
            val baseline = mid + tempSize * .36f
            val fill = ClockPalette.fill(settings.tempColor, t.ink, baseline - 22f * s, baseline)
            glowText(canvas, temp, cx, baseline, paint(ui, tempSize, 700), fill, settings.glow, centered = true)
            if (settings.hiLo) {
                val today = model.days.firstOrNull()
                val hi = today?.hi?.let { "${WeatherLogic.jsRound(it)}°" } ?: "—"
                val lo = today?.lo?.let { "${WeatherLogic.jsRound(it)}°" } ?: "—"
                val line = "$hi / $lo"
                // Under the temperature at the date's size, shrinking (not below 7 dp) only if it won't fit the block
                var size = 11f * s
                while (size > 7f * s && measure(ui, line, size, 600) > bw - 2f * s) size -= .25f
                val hb = baseline + 3f * s + size * .74f
                val hf = ClockPalette.fill(settings.tempColor, t.ink, hb - 8f * s, hb)
                glowText(canvas, line, cx, hb, paint(ui, size, 600), hf, minOf(settings.glow, 2), centered = true)
            }
            right = l - 4f * s
            // A hairline divides the clock from the weather
            canvas.drawRect(right, h * .2f, right + 1f, h * .8f, Paint().apply { color = t.ink2; alpha = (255 * .25f).toInt() })
            weatherArea = RectF(right, 0f, w, h)
        }

        // Day and date and the weather line, above or below the time. A line is one or more parts ("Wed, Oct 14" and
        // "71° · 78° / 52°") joined by a dot, all in the date's color; a line shrinks (not below 7 dp) only if it won't fit.
        val left = 4f * s
        val mid = (left + right) / 2
        val dateText = SimpleDateFormat("EEE, MMM d", Locale.getDefault()).format(Date(now))
        val wxText = if (settings.wxLine && model != null) weatherLine(model) else null
        val same = wxText != null && settings.date && settings.wxSame
        val above = mutableListOf<List<Part>>()
        val below = mutableListOf<List<Part>>()
        if (settings.date) {
            val dateLine = if (same) listOf(Part(dateText, t.ink2), Part(wxText!!, t.ink)) else listOf(Part(dateText, t.ink2))
            (if (settings.datePos == DatePos.ABOVE) above else below).add(dateLine)
        }
        if (wxText != null && !same) {
            val side = if (settings.wxPos == DatePos.ABOVE) above else below
            val beforeDate = settings.date && settings.datePos == settings.wxPos && settings.wxOrder == DatePos.ABOVE
            side.add(if (beforeDate) 0 else side.size, listOf(Part(wxText, t.ink)))
        }
        val lineSize = 11f * s
        val lineGap = 2f * s
        val maxW = right - left - 4f * s
        above.forEachIndexed { k, parts -> drawLine(canvas, parts, mid, e + lineSize * .74f + k * (lineSize + lineGap), lineSize, maxW, settings.dateColor, t) }
        below.forEachIndexed { k, parts -> drawLine(canvas, parts, mid, h - e - lineSize * .22f - (below.size - 1 - k) * (lineSize + lineGap), lineSize, maxW, settings.dateColor, t) }
        val top = if (above.isEmpty()) e + 1f * s else e + above.size * (lineSize + lineGap) - lineGap + 3f * s
        val bottom = if (below.isEmpty()) h - e - 1f * s else h - e - below.size * (lineSize + lineGap) + lineGap - 3f * s

        // The time: digits as tall as the space allows, shrunk only if the width runs out
        val face = ClockFace.byId(settings.face)
        val type = face.typeface(context)
        val time = timeText(now, settings.format, phone24)
        val dh = digitHeight(type, face)
        var size = (bottom - top) / dh
        while (size > 10f && facePaint(type, face, size).measureText(time) > right - left - 6f * s) size -= .5f
        val p = facePaint(type, face, size)
        val x = mid - p.measureText(time) / 2
        val baseline = (top + bottom) / 2 + dh * size / 2
        if (face.ghost) {
            // Unlit segments behind the LCD digits
            val ghost = facePaint(type, face, size).apply { color = t.ink; alpha = (255 * .1f).toInt() }
            canvas.drawText(time.replace(Regex("\\d"), "8"), x, baseline, ghost)
        }
        val fill = ClockPalette.fill(settings.timeColor, t.ink, baseline - dh * size, baseline)
        glowText(canvas, time, x, baseline, p, fill, settings.glow)
        return ClockRendered(bitmap, RectF(0f, 0f, right, h), weatherArea)
    }

    /** One part of a date or weather line, and its Auto color. */
    private class Part(val text: String, val auto: Int)

    /** "71° · 78° / 52°": the temperature now, then today's high and low. */
    private fun weatherLine(model: WidgetModel): String {
        val today = model.days.firstOrNull()
        val hi = today?.hi?.let { "${WeatherLogic.jsRound(it)}°" } ?: "—"
        val lo = today?.lo?.let { "${WeatherLogic.jsRound(it)}°" } ?: "—"
        return "${model.temp}° · $hi / $lo"
    }

    /** A line of [parts] centered on [cx], each in [color] (or its own Auto color), joined by a dot. */
    private fun drawLine(canvas: Canvas, parts: List<Part>, cx: Float, y: Float, fullSize: Float, maxW: Float, color: ClockColor, t: WidgetTheme) {
        val sep = "  ·  "
        fun width(size: Float) = parts.sumOf { measure(ui, it.text, size, 600).toDouble() }.toFloat() + (parts.size - 1) * measure(ui, sep, size, 600)
        var size = fullSize
        while (size > 7f * fullSize / 11f && width(size) > maxW) size -= .25f
        var x = cx - width(size) / 2
        parts.forEachIndexed { i, part ->
            if (i > 0) { shadowed(canvas, sep, x, y, paint(ui, size, 600), TextFill(t.ink2, null, false), t); x += measure(ui, sep, size, 600) }
            val p = paint(ui, size, 600)
            shadowed(canvas, part.text, x, y, p, ClockPalette.fill(color, part.auto, y - size * .74f, y), t)
            x += p.measureText(part.text)
        }
    }

    /** Small text with the soft shadow the widgets use on dark skies. */
    private fun shadowed(canvas: Canvas, s: String, x: Float, y: Float, p: TextPaint, fill: TextFill, t: WidgetTheme) {
        if (t.textShadow) {
            val sh = TextPaint(p).apply { color = 0x73000000; maskFilter = BlurMaskFilter(2f, BlurMaskFilter.Blur.NORMAL) }
            canvas.drawText(s, x, y + 1f, sh)
        }
        applyFill(p, fill)
        canvas.drawText(s, x, y, p)
    }

    private fun paint(type: Typeface, size: Float, weight: Int) = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = type
        fontVariationSettings = "'wght' $weight"
        textSize = size
    }

    private fun facePaint(type: Typeface, face: ClockFace, size: Float) = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = type
        face.variation?.let { fontVariationSettings = it }
        textSize = size
        letterSpacing = face.track
    }

    private fun measure(type: Typeface, s: String, size: Float, weight: Int) = paint(type, size, weight).measureText(s)

    /** How tall a digit is, as a share of the font size. */
    private fun digitHeight(type: Typeface, face: ClockFace): Float {
        val r = Rect()
        facePaint(type, face, 100f).getTextBounds("0", 0, 1, r)
        return if (r.height() > 0) -r.top / 100f else .72f
    }

    private fun applyFill(p: TextPaint, f: TextFill) {
        p.shader = f.shader
        p.color = f.color
    }

    /**
     * The text over the Glow setting's halo: one soft blurred pass per [Glow] layer (dark behind light fills,
     * light behind dark ones), sized with the text the same way as the other widgets' temperatures.
     */
    private fun glowText(canvas: Canvas, s: String, x: Float, baseline: Float, p: TextPaint, fill: TextFill, glow: Int, centered: Boolean = false) {
        val left = if (centered) x - p.measureText(s) / 2 else x
        val halo = if (fill.light) Glow.DARK else Glow.LIGHT
        val scale = p.textSize / 20f
        Glow.layers(glow).forEach { (radius, alpha) ->
            val hp = TextPaint(p).apply {
                shader = null
                color = (halo and 0xFFFFFF) or ((alpha * 255).toInt() shl 24)
                maskFilter = BlurMaskFilter(max(radius * scale, .1f), BlurMaskFilter.Blur.NORMAL)
            }
            canvas.drawText(s, left, baseline, hp)
        }
        applyFill(p, fill)
        canvas.drawText(s, left, baseline, p)
    }

    companion object {
        /** Hours and minutes only, no AM or PM. Phone follows the phone's 12- or 24-hour setting. */
        fun timeText(now: Long, format: TimeFormat, phone24: Boolean): String {
            val c = Calendar.getInstance().apply { timeInMillis = now }
            val h = c.get(Calendar.HOUR_OF_DAY)
            val m = c.get(Calendar.MINUTE)
            val use24 = when (format) { TimeFormat.PHONE -> phone24; TimeFormat.H12 -> false; TimeFormat.H24 -> true }
            return if (use24) String.format(Locale.US, "%02d:%02d", h, m) else String.format(Locale.US, "%d:%02d", if (h % 12 == 0) 12 else h % 12, m)
        }
    }
}
