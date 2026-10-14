package com.prostellis.awe.widget

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.text.TextPaint
import android.text.TextUtils
import com.prostellis.awe.R
import com.prostellis.awe.weather.Cat
import com.prostellis.awe.weather.Daylight
import com.prostellis.awe.weather.Hour
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

/** A drawn widget plus where the refresh button (every widget) and the settings button (forecast widget only) go on top of it. */
class Rendered(val bitmap: Bitmap, val refresh: ButtonSpot, val gear: ButtonSpot?, val buttonColor: Int)

/** AWE 1×1 (weather tint), AWE Daily and AWE Hourly (2×1 up to 5×1), and the 4×2 Forecast. */
enum class WidgetKind { TINY, DAILY, HOURLY, FORECAST }

/** How a widget is drawn: its own settings, the shared styles, and what sits behind it. */
data class Look(
    val transparency: Int,
    val theme: WidgetTheme,
    /** The phone is in dark mode (Basic's sky uses the app's dark palette). */
    val phoneDark: Boolean,
    val iconStyle: IconStyle = IconStyle.GLOW,
    /** A [TempColors] choice; 0 = Auto. */
    val tempColor: Int = 0,
    /** The background style's still picture; null draws Basic's sky. */
    val sky: SkyPicture? = null,
    val showCurrent: Boolean = true,
    /** The wallpaper is light (for the 1×1's Auto temperature color when see-through). */
    val wallpaperLight: Boolean = false,
    /** A [Glow] step for the halo behind temperatures. */
    val glow: Int = Glow.DEFAULT,
)

/**
 * Draws the widgets exactly as approved in the mock-ups. Everything is laid out in dp and drawn
 * into a bitmap at the phone's density; the buttons are separate views placed on top.
 */
class WidgetRenderer(private val context: Context) {

    private val density = context.resources.displayMetrics.density
    private val cornerDp = context.resources.getDimension(android.R.dimen.system_app_widget_background_radius) / density
    private val font: Typeface = context.resources.getFont(R.font.dm_sans)
    private val timeFormat = SimpleDateFormat("h:mm a", Locale.US)
    private val dayFormat = SimpleDateFormat("EEE", Locale.US)
    private var icons = IconStyle.GLOW
    private var eventColors: Map<String, Int> = emptyMap()

    fun render(kind: WidgetKind, model: WidgetModel?, widthDp: Float, heightDp: Float, look: Look, events: Map<String, Int> = emptyMap()): Rendered {
        icons = look.iconStyle
        eventColors = events
        val w = max(widthDp, 40f)
        val h = max(heightDp, 40f)
        val bitmap = Bitmap.createBitmap((w * density).toInt(), (h * density).toInt(), Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.scale(density, density)
        val bgAlpha = 1f - look.transparency / 100f
        val cat = model?.nowCat ?: Cat.PARTLY
        val light = model?.light ?: Daylight(1.0, false, true)
        when {
            kind == WidgetKind.TINY -> drawTint(canvas, w, h, cat, light.isDay, bgAlpha)
            look.sky != null -> drawPicture(canvas, w, h, look.sky, look.theme, bgAlpha)
            else -> drawSky(canvas, w, h, cat, light, look.phoneDark, bgAlpha)
        }
        return when (kind) {
            WidgetKind.TINY -> drawTiny(canvas, bitmap, w, h, model, look)
            WidgetKind.DAILY -> drawStrip(canvas, bitmap, w, h, model, look, hourly = false)
            WidgetKind.HOURLY -> drawStrip(canvas, bitmap, w, h, model, look, hourly = true)
            WidgetKind.FORECAST -> drawForecast(canvas, bitmap, w, h, model, look)
        }
    }

    /* ---------------- Background ---------------- */

    private fun rounded(w: Float, h: Float) = Path().apply { addRoundRect(RectF(0f, 0f, w, h), cornerDp, cornerDp, Path.Direction.CW) }

    /** The style's still picture, filling the widget (cropped from the middle), with a light veil so text reads. */
    private fun drawPicture(canvas: Canvas, w: Float, h: Float, sky: SkyPicture, t: WidgetTheme, alpha: Float) {
        if (alpha <= 0f) return
        val b = sky.bitmap
        val src = if (b.width / b.height.toFloat() > w / h) {
            val sw = (b.height * w / h).toInt(); Rect((b.width - sw) / 2, 0, (b.width + sw) / 2, b.height)
        } else {
            val sh = (b.width * h / w).toInt(); Rect(0, (b.height - sh) / 2, b.width, (b.height + sh) / 2)
        }
        canvas.save()
        canvas.clipPath(rounded(w, h))
        canvas.drawBitmap(b, src, RectF(0f, 0f, w, h), Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply { this.alpha = (255 * alpha).toInt() })
        canvas.drawColor((((t.veil ushr 24) * alpha).toInt() shl 24) or (t.veil and 0xFFFFFF))
        canvas.restore()
    }

    /** The 1×1's square, tinted by the weather: warm for sun, blue-gray for rain, navy at night. */
    private fun drawTint(canvas: Canvas, w: Float, h: Float, cat: Cat, isDay: Boolean, alpha: Float) {
        if (alpha <= 0f) return
        val (c1, c2) = tint(cat, isDay)
        // 160° CSS gradient
        val rad = Math.toRadians(160.0 - 90.0)
        val len = (w * cos(rad) + h * sin(rad)).toFloat() / 2f
        val dx = (cos(rad) * len).toFloat(); val dy = (sin(rad) * len).toFloat()
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        paint.shader = LinearGradient(w / 2 - dx, h / 2 - dy, w / 2 + dx, h / 2 + dy, c1, c2, Shader.TileMode.CLAMP)
        paint.alpha = (255 * alpha).toInt()
        canvas.drawPath(rounded(w, h), paint)
    }

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
        canvas.clipPath(rounded(w, h))
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

    /* ---------------- AWE 1×1: weather tint ---------------- */

    private fun drawTiny(canvas: Canvas, bitmap: Bitmap, w: Float, h: Float, m: WidgetModel?, look: Look): Rendered {
        val z = min(w, h)
        val refresh = cornerRefresh(w)
        if (m == null) {
            centered(canvas, "…", w / 2, h / 2 + 6f, 18f, 600, look.theme.ink2, look.theme)
            return Rendered(bitmap, refresh, null, look.theme.ink2)
        }
        val (c1, c2) = tint(m.nowCat, m.light.isDay)
        val tile = WeatherLogic.mix(c1, .5, c2)
        // The weather icon as a large, faint watermark
        val iconColors = if (TempColors.luminance(tile) > .3) WidgetTheme.LIGHT.icons else WidgetTheme.DARK.icons
        val size = z * 1.16f
        canvas.saveLayerAlpha(RectF(0f, 0f, w, h), (255 * .4f).toInt())
        canvas.clipPath(rounded(w, h))
        WeatherIcons.draw(canvas, m.nowCat, m.light.isDay, (w - size) / 2, (h - size) / 2, size, iconColors, icons)
        canvas.restore()
        // Auto: white or near-black against the tint (and the wallpaper, as it shows through)
        val wall = if (look.wallpaperLight) 0xFFDFE6F0.toInt() else 0xFF1D2232.toInt()
        val behind = WeatherLogic.mix(wall, look.transparency / 100.0, tile)
        val color = TempColors.color(look.tempColor, TempColors.readableOn(behind))
        val big = z * .44f; val small = big * .5f
        val num = "${m.temp}"
        val numW = measure(num, big, 700, -.02f * big); val degW = measure("°", small, 700)
        val x = (w - numW - degW) / 2
        val baseline = h / 2 + big * .36f
        glowing(canvas, num, x, baseline, paint(big, 700, color, null, -.02f * big, shadow = false), look.glow)
        glowing(canvas, "°", x + numW, baseline - small * .46f, paint(small, 700, color, null, shadow = false), look.glow)
        // The refresh button in white or near-black against the tint, like Auto
        return Rendered(bitmap, refresh, null, TempColors.readableOn(behind))
    }

    /** The small refresh button in the top-right corner of the 1×1, Daily and Hourly widgets. */
    private fun cornerRefresh(w: Float) = ButtonSpot(w - 6f - CORNER_ICON / 2, 6f + CORNER_ICON / 2, CORNER_ICON)

    /* ---------------- AWE Daily and AWE Hourly: the current weather, then day or hour tiles ---------------- */

    private fun drawStrip(canvas: Canvas, bitmap: Bitmap, w: Float, h: Float, m: WidgetModel?, look: Look, hourly: Boolean): Rendered {
        val t = look.theme
        // Designed 74 dp tall (one row); grows a little with taller rows
        val s = (h / 74f).coerceIn(.85f, 1.3f)
        val refresh = cornerRefresh(w)
        // A narrow column on the right keeps the refresh button clear of the text and tiles
        val right = refresh.centerX - refresh.iconSize / 2 - 2f
        if (m == null) {
            text(canvas, "Loading forecast…", 12f, h / 2f + 4f, 12f, 500, t.ink2, t)
            return Rendered(bitmap, refresh, null, t.ink2)
        }
        val cols = columns(w)
        val count = if (hourly) min(HOUR_CELLS[cols], m.hours.size) else min(DAY_CELLS[cols], m.days.size)
        val tempColor = TempColors.color(look.tempColor, t.ink)

        if (count == 0) {
            // Full current block: icon, then place, temperature and condition
            val icon = 34f * s
            WeatherIcons.draw(canvas, m.nowCat, m.light.isDay, 10f * s, (h - icon) / 2, icon, t.icons, icons, t.textShadow)
            val x = 10f * s + icon + 6f * s
            val room = right - x - 4f
            val placeSize = 10f * s; val tempSize = 26f * s; val condSize = 10f * s
            val top = (h - (placeSize * 1.25f + tempSize + condSize * 1.25f + 4f * s)) / 2
            text(canvas, fit(fullPlace(m), placeSize, 600, room), x, top + placeSize, placeSize, 600, t.ink2, t)
            val tempBase = top + placeSize * 1.25f + 2f * s + tempSize * .9f
            tempText(canvas, fit("${m.temp}°", tempSize, 700, room), x, tempBase, tempSize, 700, tempColor, look.glow)
            text(canvas, fit(m.nowText, condSize, 600, room), x, tempBase + 2f * s + condSize * 1.15f, condSize, 600, t.ink2, t)
            return Rendered(bitmap, refresh, null, t.ink2)
        }

        // Slim current block: place, icon and temperature, condition
        val curW = 78f * s
        val label = 9.5f * s; val icon = 22f * s; val temp = 22f * s
        val top = (h - (label * 1.2f * 2 + temp + 4f * s)) / 2
        centered(canvas, fit(m.place.name, label, 600, curW - 8f), curW / 2, top + label, label, 600, t.ink2, t)
        val tempText = "${m.temp}°"
        val rowW = icon + 3f * s + measure(tempText, temp, 700)
        val rowTop = top + label * 1.2f + 2f * s
        WeatherIcons.draw(canvas, m.nowCat, m.light.isDay, (curW - rowW) / 2, rowTop + (temp - icon) / 2, icon, t.icons, icons, t.textShadow)
        tempText(canvas, tempText, (curW - rowW) / 2 + icon + 3f * s, rowTop + temp * .86f, temp, 700, tempColor, look.glow)
        centered(canvas, fit(m.nowText, label, 600, curW - 8f), curW / 2, rowTop + temp + 2f * s + label, label, 600, t.ink2, t)

        // Tiles: each day (name, icon, high over low) or hour (time, icon, temperature)
        val area = RectF(curW, 0f, right, h)
        val cellW = area.width() / count
        val tilePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = if (look.sky != null) t.panel else if (t.dark) 0x0FFFFFFF else 0x0D14202B
            if (look.sky != null) alpha = (((t.panel ushr 24) and 0xFF) * (1f - look.transparency * .7f / 100f)).toInt()
        }
        val cellIcon = (if (hourly) 18f else 20f) * s
        val main = 12.5f * s
        for (i in 0 until count) {
            val tile = RectF(area.left + i * cellW + 2f * s, 3f * s, area.left + (i + 1) * cellW - 2f * s, h - 3f * s)
            canvas.drawRoundRect(tile, 10f * s, 10f * s, tilePaint)
            val cx = tile.centerX()
            val sub = !hourly
            val block = label * 1.2f + .5f * s + cellIcon + main * 1.15f + (if (sub) label * 1.2f else 0f)
            var y = tile.centerY() - block / 2
            val name: String; val cat: Cat; val day: Boolean; val big: String; var small: String? = null
            if (hourly) {
                val hr: Hour = m.hours[i]
                name = hr.label; cat = hr.cat; day = hr.isDay; big = "${hr.temp}°"
            } else {
                val d = m.days[i]
                name = if (i == 0) "Today" else dayFormat.format(java.sql.Date.valueOf(d.ymd)); cat = d.cat; day = true
                big = d.hi?.let { "${WeatherLogic.jsRound(it)}°" } ?: "—"
                small = d.lo?.let { "${WeatherLogic.jsRound(it)}°" } ?: "—"
            }
            centered(canvas, fit(name, label, 700, tile.width() - 2f), cx, y + label, label, 700, t.ink2, t)
            y += label * 1.2f + .5f * s
            WeatherIcons.draw(canvas, cat, day, cx - cellIcon / 2, y, cellIcon, t.icons, icons, t.textShadow)
            y += cellIcon
            tempText(canvas, big, cx, y + main, main, 700, tempColor, look.glow, centered = true)
            y += main * 1.15f
            small?.let { centered(canvas, it, cx, y + label, label, 600, t.ink2, t) }
        }
        return Rendered(bitmap, refresh, null, t.ink2)
    }

    /** "Salem, Oregon": the place with its full state or region, where there's room for it. */
    private fun fullPlace(m: WidgetModel) = listOf(m.place.name, m.place.region).filter { it.isNotBlank() }.joinToString(", ")

    /* ---------------- Forecast (5 days, 7 when wide) ---------------- */

    /** Text and icon sizes for the forecast widget at a given scale (1 = the approved mock-up). */
    private class Sizes(s: Float) {
        val bandH = 50f * s; val bandIcon = 36f * s; val bandTemp = 34f * s
        val cond = 16.5f * s; val place = 13.5f * s; val updated = 11.5f
        val name = 15f * s; val icon = 28f * s; val hi = 18f * s; val lo = 16f * s; val amt = 12f * s
        val bar = 4.5f * s
    }

    private fun drawForecast(canvas: Canvas, bitmap: Bitmap, w: Float, h: Float, m: WidgetModel?, look: Look): Rendered {
        val t = look.theme
        val transparency = look.transparency
        val showCurrent = look.showCurrent
        val tempColor = TempColors.color(look.tempColor, t.ink)
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

            WeatherIcons.draw(canvas, m.nowCat, m.light.isDay, band.left + 9f, band.centerY() - z.bandIcon / 2, z.bandIcon, t.icons, icons, t.textShadow)
            val tempText = "${m.temp}°"
            val tempX = band.left + 9f + z.bandIcon + 9f
            tempText(canvas, tempText, tempX, band.centerY() + z.bandTemp * .36f, z.bandTemp, 600, tempColor, look.glow, letterSpacingDp = -1f)
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

        drawDays(canvas, RectF(padX, band.bottom + gap, w - padX, h - padBottom), m.days.take(dayCount), z, t, tempColor, look.glow)
        return Rendered(bitmap, refresh, gear, t.ink2)
    }

    private fun drawAwe(canvas: Canvas, x: Float, baseline: Float, size: Float, t: WidgetTheme) {
        var cx = x
        for ((ch, color) in listOf("A" to t.awe.first, "W" to t.awe.second, "E" to t.awe.third)) {
            text(canvas, ch, cx, baseline, size, 800, color, t, letterSpacingDp = .6f)
            cx += measure(ch, size, 800, .6f)
        }
    }

    private fun drawDays(canvas: Canvas, area: RectF, days: List<com.prostellis.awe.weather.Day>, z: Sizes, t: WidgetTheme, tempColor: Int, glow: Int) {
        if (days.isEmpty()) return
        val gap = 2f
        val colW = (area.width() - gap * (days.size - 1)) / days.size
        val temps = days.flatMap { listOfNotNull(it.hi, it.lo) }
        val wMax = temps.maxOrNull() ?: 0.0
        val wMin = temps.minOrNull() ?: 0.0
        val colTop = 4f; val amtBottom = 3f
        val nameH = z.name * 1.35f; val iconH = z.icon + 1f; val amtH = z.amt * 1.1f // nameH leaves room for the event dot
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
            // Event day (holiday, season, sky event): a small dot in the sticker's color under the name
            eventColors[d.ymd]?.let { color ->
                canvas.drawCircle(cx, area.top + colTop + z.name * 1.2f, 2.2f * z.name / 15f, Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color })
            }
            WeatherIcons.draw(canvas, d.cat, true, cx - z.icon / 2, area.top + colTop + nameH, z.icon, t.icons, icons, t.textShadow)

            if (d.hi != null && d.lo != null) {
                val top = y(d.hi)
                val bottom = y(d.lo) + minBar
                val bar = Paint(Paint.ANTI_ALIAS_FLAG)
                bar.shader = LinearGradient(0f, top, 0f, bottom, WeatherLogic.tempColor(d.hi), WeatherLogic.tempColor(d.lo), Shader.TileMode.CLAMP)
                canvas.drawRoundRect(RectF(cx - z.bar / 2, top, cx + z.bar / 2, bottom), z.bar, z.bar, bar)
                tempText(canvas, "${WeatherLogic.jsRound(d.hi)}°", cx, top - 4f, z.hi, 700, tempColor, glow, centered = true)
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

    private fun paint(sizeDp: Float, weight: Int, color: Int, t: WidgetTheme?, letterSpacingDp: Float = 0f, shadow: Boolean = t?.textShadow == true) =
        TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = font
            fontVariationSettings = "'wght' $weight"
            textSize = sizeDp
            this.color = color
            letterSpacing = letterSpacingDp / sizeDp
            if (shadow) setShadowLayer(2f, 0f, 1f, 0x73000000)
        }

    private fun text(
        canvas: Canvas, s: String, x: Float, baseline: Float, size: Float, weight: Int, color: Int, t: WidgetTheme,
        letterSpacingDp: Float = 0f, shadow: Boolean = t.textShadow,
    ) {
        canvas.drawText(s, x, baseline, paint(size, weight, color, t, letterSpacingDp, shadow))
    }

    private fun centered(canvas: Canvas, s: String, cx: Float, baseline: Float, size: Float, weight: Int, color: Int, t: WidgetTheme, shadow: Boolean = t.textShadow) {
        val p = paint(size, weight, color, t, shadow = shadow)
        canvas.drawText(s, cx - p.measureText(s) / 2f, baseline, p)
    }

    /** A temperature, with the Glow strength setting's halo behind it. */
    private fun tempText(
        canvas: Canvas, s: String, x: Float, baseline: Float, size: Float, weight: Int, color: Int, glow: Int,
        letterSpacingDp: Float = 0f, centered: Boolean = false,
    ) {
        val p = paint(size, weight, color, null, letterSpacingDp, shadow = false)
        glowing(canvas, s, if (centered) x - p.measureText(s) / 2f else x, baseline, p, glow)
    }

    /**
     * Draws [s] once per [Glow] layer, each pass adding a soft halo (dark behind light colors, light behind dark ones),
     * so the halo builds up the way stacked text-shadows do in the app. Sizes scale with the text.
     */
    private fun glowing(canvas: Canvas, s: String, x: Float, baseline: Float, p: TextPaint, glow: Int) {
        val halo = if (TempColors.isLight(p.color)) Glow.DARK else Glow.LIGHT
        val scale = p.textSize / 20f
        Glow.layers(glow).forEach { (radius, alpha) ->
            p.setShadowLayer(radius * scale, 0f, 0f, (halo and 0xFFFFFF) or ((alpha * 255).toInt() shl 24))
            canvas.drawText(s, x, baseline, p)
        }
        p.clearShadowLayer()
        canvas.drawText(s, x, baseline, p)
    }

    private fun measure(s: String, size: Float, weight: Int, letterSpacingDp: Float = 0f) =
        paint(size, weight, 0, null, letterSpacingDp).measureText(s)

    private fun fit(s: String, size: Float, weight: Int, maxW: Float): String =
        TextUtils.ellipsize(s, paint(size, weight, 0, null), max(0f, maxW), TextUtils.TruncateAt.END).toString()

    companion object {
        /** The corner refresh button's icon size, in dp. */
        private const val CORNER_ICON = 14f

        /** Wide enough for 7 days: a 5-column-wide widget, or a full-width one. */
        const val SEVEN_DAY_MIN_WIDTH_DP = 340f

        fun isSevenDay(widthDp: Float) = widthDp >= SEVEN_DAY_MIN_WIDTH_DP

        /** How many home-screen columns a widget spans (2 to 5), from its width (about 78 dp a column). */
        fun columns(widthDp: Float) = when {
            widthDp >= SEVEN_DAY_MIN_WIDTH_DP -> 5
            widthDp >= 262f -> 4
            widthDp >= 184f -> 3
            else -> 2
        }

        /** Day tiles beside the current weather on AWE Daily, by columns: 2×1 none, 3×1 today and tomorrow, 4×1 five, 5×1 six. */
        val DAY_CELLS = intArrayOf(0, 0, 0, 2, 5, 6)

        /** Hour tiles on AWE Hourly, by columns: the next 1, 3, 5 or 7 hours. */
        val HOUR_CELLS = intArrayOf(0, 0, 1, 3, 5, 7)

        /** The 1×1's tint by weather: day top, day bottom, night top, night bottom (from the approved mock-up). */
        private val TINT = mapOf(
            Cat.CLEAR to intArrayOf(0xFFD98A, 0x8FC8F2, 0x26386A, 0x121A3A), Cat.PARTLY to intArrayOf(0xCFE4F6, 0x9FC3E6, 0x2C3B62, 0x151D36),
            Cat.CLOUDY to intArrayOf(0xD8DFE7, 0xAAB7C5, 0x2F3946, 0x1C232D), Cat.FOG to intArrayOf(0xE3E6EA, 0xC2C8CF, 0x353B43, 0x1F2328),
            Cat.DRIZZLE to intArrayOf(0x9FB4CB, 0x5F7894, 0x28364A, 0x151D29), Cat.RAIN to intArrayOf(0x9FB4CB, 0x5F7894, 0x28364A, 0x151D29),
            Cat.SNOW to intArrayOf(0xEEF4FA, 0xC4D6E8, 0x33405A, 0x1D2538), Cat.STORM to intArrayOf(0x8F87B0, 0x4F4A73, 0x2A2540, 0x14111F),
        )

        fun tint(cat: Cat, isDay: Boolean): Pair<Int, Int> {
            val c = TINT.getValue(cat); val k = if (isDay) 0 else 2
            return (0xFF000000.toInt() or c[k]) to (0xFF000000.toInt() or c[k + 1])
        }

        /** Star spots near the edges, clear of the text. */
        private val STARS = listOf(.04f to .06f, .52f to .03f, .97f to .52f, .02f to .60f, .98f to .88f)
    }
}
