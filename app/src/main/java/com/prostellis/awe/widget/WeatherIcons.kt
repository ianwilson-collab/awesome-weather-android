package com.prostellis.awe.widget

import android.graphics.Canvas
import android.graphics.DashPathEffect
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import androidx.core.graphics.PathParser
import com.prostellis.awe.weather.Cat
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * The weather-symbol style chosen in the app or a widget's settings, in the order they're offered: Basic, Fine Outline
 * and 3D. [id] is the web app's name for it (3D was called Glow, and keeps that id so saved choices stay as they are).
 */
enum class IconStyle(val id: String) {
    BASIC("default"), FINE("fine"), THREE_D("glow");

    companion object {
        /** People who never picked a style get 3D, as in the app. */
        fun fromId(id: String?): IconStyle = entries.firstOrNull { it.id == id } ?: THREE_D
    }
}

/** Colors the weather icons use (the web app's --ink-2, --sun-ink, --glow-cloud-a, … variables). */
data class IconColors(
    val stroke: Int, val ink3: Int, val sun: Int, val cloudFill: Int, val rain: Int,
    val moonInk: Int, val moonIcon: Int, val crater: Int,
    val glowA: Int, val glowB: Int, val glowShadow: Int, val lineInk: Int,
)

/** The web app's inline-SVG weather icons in all three styles, drawn on a 24×24 grid with the same shapes. */
object WeatherIcons {
    private fun path(d: String): Path = PathParser.createPathFromPathData(d)

    private val CLOUD = path("M7 19a4 4 0 0 1-.5-7.97A5.5 5.5 0 0 1 17 10a4.5 4.5 0 0 1 0 9z")
    private val CRESCENT = path("M19.5 14.5A8 8 0 1 1 9.5 4.5a6.5 6.5 0 0 0 10 10z")
    private val BOLT = path("M12.5 15.5l-2.5 4h3l-1.5 3.5")
    private val GLOW_BOLT = path("M13 13.2l-3 4.6h3.2l-1.7 4.4 4.8-6.2h-3.3l1.6-2.8z")
    private val GLOW_MOON = path(crescent())
    private val FINE_MOON = path("M16.8 15.6A6.8 6.8 0 1 1 9.2 6.2a5.4 5.4 0 0 0 7.6 9.4z")
    private val FINE_STAR = path("M17.5 4.5v3M16 6h3M20 10.2v1.6M19.2 11h1.6")
    private val FINE_CLOUD_LINE = path("M9.3 12.4a3.6 3.6 0 0 1 3.2-1.4")
    private val FINE_FOG = path("M4.5 18.6c1.5-1 3-1 4.5 0s3 1 4.5 0 3-1 4.5 0 1.5.6 1.5.6M7 21.6c1.3-.9 2.7-.9 4 0s2.7.9 4 0 2-.6 2-.6")
    private val FINE_DRIZZLE = path("M8 18.8v1.2M12 20v1.2M16 18.8v1.2M10 22.2v1M14 22.2v1")
    private val FINE_BOLT = path("M13.2 14.2l-3 4.4h3l-2 4.4")

    /** 3D clouds are three puffs on a rounded base: (center x, center y, radius). */
    private val PUFFS = listOf(Triple(13.6f, 11.6f, 5.3f), Triple(8.6f, 14f, 4.2f), Triple(18f, 15.1f, 3.6f))
    private val BASE = RectF(5f, 14f, 20f, 18.9f)
    private val FLAKES = listOf(7.4f to 19.7f, 16.6f to 19.7f, 12f to 22.1f)

    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND
    }
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private var lineWidth = 1.7f

    /** Draws the icon for a category with its top-left at (x, y), [size] wide. */
    fun draw(
        canvas: Canvas, cat: Cat, isDay: Boolean, x: Float, y: Float, size: Float, c: IconColors,
        style: IconStyle, shadow: Boolean = false,
    ) {
        canvas.save()
        canvas.translate(x, y)
        canvas.scale(size / 24f, size / 24f)
        if (shadow) {
            stroke.setShadowLayer(1.2f, 0f, .6f, 0x55000000); fill.setShadowLayer(1.2f, 0f, .6f, 0x55000000)
        } else {
            stroke.clearShadowLayer(); fill.clearShadowLayer()
        }
        lineWidth = if (style == IconStyle.FINE) 1.1f else 1.7f
        reset()
        when (cat) {
            Cat.CLEAR -> if (isDay) sun(canvas, style, c) else moon(canvas, style, c)
            Cat.PARTLY -> {
                when (style) {
                    IconStyle.BASIC -> if (isDay) basicSun(canvas, 8.5f, 8f, 2.8f, 1.6f, c) else at(canvas, -3f, -3f, .62f) { crescent(canvas, c) }
                    else -> if (isDay) around(canvas, 8.4f, 8f, .62f) { sun(canvas, style, c) } else around(canvas, 8.6f, 8.2f, .62f) { smallMoon(canvas, style, c) }
                }
                cloud(canvas, style, c, 3f, 3f, .86f)
            }
            Cat.CLOUDY -> cloud(canvas, style, c)
            Cat.FOG -> { cloud(canvas, style, c, 0f, -4f); fog(canvas, style, c) }
            Cat.DRIZZLE -> { cloud(canvas, style, c, 0f, -3f); drizzle(canvas, style, c) }
            Cat.RAIN -> { cloud(canvas, style, c, 0f, -3f); rain(canvas, style, c) }
            Cat.SNOW -> { cloud(canvas, style, c, 0f, -3f); snowflakes(canvas) }
            Cat.STORM -> { cloud(canvas, style, c, 0f, -3f); storm(canvas, style, c) }
        }
        reset()
        canvas.restore()
    }

    /* ---------------- shared helpers ---------------- */

    private fun reset() {
        stroke.shader = null; stroke.pathEffect = null; stroke.strokeWidth = lineWidth; stroke.alpha = 255
        fill.shader = null
    }

    private fun alpha(rgb: Int, a: Float): Int = ((a * 255).roundToInt() shl 24) or (rgb and 0xFFFFFF)

    /** Fills with a gradient (the paint's own color must be opaque, or it fades the gradient). */
    private fun shade(shader: Shader): Paint { fill.color = 0xFF000000.toInt(); fill.shader = shader; return fill }

    private fun radial(cx: Float, cy: Float, r: Float, colors: IntArray, stops: FloatArray) =
        RadialGradient(cx, cy, r, colors, stops, Shader.TileMode.CLAMP)

    private fun vertical(top: Float, bottom: Float, from: Int, to: Int) =
        LinearGradient(0f, top, 0f, bottom, from, to, Shader.TileMode.CLAMP)

    private inline fun at(canvas: Canvas, dx: Float, dy: Float, k: Float, block: () -> Unit) {
        canvas.save(); canvas.translate(dx, dy); canvas.scale(k, k); block(); canvas.restore()
    }

    /** Draws a full-size (24×24) shape shrunk by [k] around (cx, cy), like the web app's at(). */
    private inline fun around(canvas: Canvas, cx: Float, cy: Float, k: Float, block: () -> Unit) {
        canvas.save(); canvas.translate(cx, cy); canvas.scale(k, k); canvas.translate(-12f, -12f); block(); canvas.restore()
    }

    private fun rays(canvas: Canvas, cx: Float, cy: Float, r1: Float, r2: Float, n: Int) {
        for (k in 0 until n) {
            val a = k * 2 * Math.PI / n
            canvas.drawLine(
                (cx + cos(a) * r1).toFloat(), (cy + sin(a) * r1).toFloat(),
                (cx + cos(a) * r2).toFloat(), (cy + sin(a) * r2).toFloat(), stroke,
            )
        }
    }

    /** Raindrop shape centered near (x, y), scaled by [k] (same path as the web app's dropPath). */
    private fun drop(x: Float, y: Float, k: Float): Path = path(
        "M$x ${y - 2 * k}c${1.1f * k} ${1.5f * k} ${1.7f * k} ${2.4f * k} ${1.7f * k} ${3.1f * k}" +
            "a${1.7f * k} ${1.7f * k} 0 0 1 ${-3.4f * k} 0c0 ${-.7f * k} ${.6f * k} ${-1.6f * k} ${1.7f * k} ${-3.1f * k}z",
    )

    /** Crescent: the part of a circle outside a second, offset circle (same as the web app's crescentPath). */
    private fun crescent(cx: Float = 12f, cy: Float = 12f, r1: Float = 7.6f, ox: Float = 16f, oy: Float = 8.4f, r2: Float = 6.3f): String {
        val dx = ox - cx; val dy = oy - cy; val d = hypot(dx, dy)
        val a = (r1 * r1 - r2 * r2 + d * d) / (2 * d); val h = sqrt(r1 * r1 - a * a)
        val px = cx + a * dx / d; val py = cy + a * dy / d; val ux = -dy / d * h; val uy = dx / d * h
        return "M${px + ux} ${py + uy}A$r1 $r1 0 1 1 ${px - ux} ${py - uy}A$r2 $r2 0 0 0 ${px + ux} ${py + uy}z"
    }

    /** Snow in every style: thin white six-point snowflakes, each with a soft blue aura. */
    private fun snowflakes(canvas: Canvas) {
        for ((x, y) in FLAKES) {
            canvas.drawCircle(x, y, 2.5f, shade(radial(x, y, 2.5f, intArrayOf(alpha(0x7FB0EA, .55f), alpha(0x7FB0EA, .55f), alpha(0x7FB0EA, 0f)), floatArrayOf(0f, .35f, 1f))))
        }
        fill.shader = null
        stroke.color = 0xFFFFFFFF.toInt(); stroke.strokeWidth = .75f
        for ((x, y) in FLAKES) for (deg in listOf(0, 60, 120)) {
            val a = Math.toRadians(deg.toDouble()); val cx = (cos(a) * 1.55).toFloat(); val sy = (sin(a) * 1.55).toFloat()
            canvas.drawLine(x - cx, y - sy, x + cx, y + sy, stroke)
        }
        stroke.strokeWidth = lineWidth
    }

    /* ---------------- per style ---------------- */

    private fun sun(canvas: Canvas, style: IconStyle, c: IconColors) = when (style) {
        IconStyle.BASIC -> basicSun(canvas, 12f, 12f, 4.2f, 2.6f, c)
        IconStyle.THREE_D -> {
            canvas.drawCircle(12f, 12f, 11f, shade(radial(12f, 12f, 11f, intArrayOf(alpha(0xFFC640, .55f), alpha(0xFFC640, .55f), alpha(0xFFC640, 0f)), floatArrayOf(0f, .35f, 1f))))
            // SVG: radial gradient centered at 38% / 35% of the 10.8-wide circle, radius 70% of it
            canvas.drawCircle(12f, 12f, 5.4f, shade(radial(10.704f, 10.38f, 7.56f, intArrayOf(0xFFFFF4B8.toInt(), 0xFFFFBE2E.toInt(), 0xFFFF8F1F.toInt()), floatArrayOf(0f, .55f, 1f))))
            fill.shader = null
        }
        IconStyle.FINE -> {
            stroke.color = c.sun
            canvas.drawCircle(12f, 12f, 5f, stroke)
            rays(canvas, 12f, 12f, 7f, 8.9f, 12)
        }
    }

    private fun basicSun(canvas: Canvas, cx: Float, cy: Float, r: Float, ray: Float, c: IconColors) {
        fill.color = c.sun; fill.alpha = (255 * .25f).toInt()
        canvas.drawCircle(cx, cy, r, fill)
        stroke.color = c.sun
        canvas.drawCircle(cx, cy, r, stroke)
        rays(canvas, cx, cy, r + 2, r + 2 + ray, 8)
    }

    private fun moon(canvas: Canvas, style: IconStyle, c: IconColors) {
        when (style) {
            IconStyle.BASIC -> {
                fill.color = c.moonIcon
                canvas.drawCircle(12f, 12f, 7.5f, fill)
                stroke.color = c.ink3; stroke.strokeWidth = 1.4f
                canvas.drawCircle(12f, 12f, 7.5f, stroke)
                stroke.strokeWidth = lineWidth
                fill.color = c.crater
                canvas.drawCircle(9.4f, 9.8f, 1.7f, fill); canvas.drawCircle(14.6f, 13.4f, 1.3f, fill)
                canvas.drawCircle(11f, 15.4f, .9f, fill); canvas.drawCircle(14.2f, 8.6f, .7f, fill)
            }
            IconStyle.THREE_D -> {
                canvas.drawCircle(12f, 12f, 11f, shade(radial(12f, 12f, 11f, intArrayOf(alpha(0xFFF2C2, .45f), alpha(0xFFF2C2, .45f), alpha(0xFFF2C2, 0f)), floatArrayOf(0f, .4f, 1f))))
                glowMoonFill(canvas, GLOW_MOON)
            }
            IconStyle.FINE -> {
                stroke.color = c.lineInk; canvas.drawPath(FINE_MOON, stroke)
                stroke.color = c.sun; canvas.drawPath(FINE_STAR, stroke)
            }
        }
    }

    /** Pale gold, lighter at the top-left (diagonal across the shape's own bounds). */
    private fun glowMoonFill(canvas: Canvas, shape: Path) {
        val b = RectF(); shape.computeBounds(b, true)
        canvas.drawPath(shape, shade(LinearGradient(b.left, b.top, b.right, b.bottom, 0xFFFFF8DC.toInt(), 0xFFE3C873.toInt(), Shader.TileMode.CLAMP)))
        fill.shader = null
    }

    private fun smallMoon(canvas: Canvas, style: IconStyle, c: IconColors) {
        when (style) {
            IconStyle.BASIC -> crescent(canvas, c)
            IconStyle.THREE_D -> glowMoonFill(canvas, CRESCENT)
            IconStyle.FINE -> {
                fill.color = c.cloudFill; canvas.drawPath(CRESCENT, fill)
                stroke.color = c.lineInk; canvas.drawPath(CRESCENT, stroke)
            }
        }
    }

    private fun crescent(canvas: Canvas, c: IconColors) {
        fill.color = c.moonInk
        canvas.drawPath(CRESCENT, fill)
        stroke.color = c.stroke
        canvas.drawPath(CRESCENT, stroke)
    }

    private fun cloud(canvas: Canvas, style: IconStyle, c: IconColors, dx: Float = 0f, dy: Float = 0f, scale: Float = 1f) {
        when (style) {
            IconStyle.BASIC -> at(canvas, dx, dy, scale) {
                fill.color = c.cloudFill; canvas.drawPath(CLOUD, fill)
                stroke.color = c.stroke; canvas.drawPath(CLOUD, stroke)
            }
            IconStyle.THREE_D -> {
                // Soft shadow first, nudged down and right
                at(canvas, dx, dy, scale) { canvas.save(); canvas.translate(.4f, 1f); puffs(canvas) { fill.color = c.glowShadow; fill }; canvas.restore() }
                at(canvas, dx, dy, scale) { sunlitCloud(canvas, c) }
            }
            IconStyle.FINE -> at(canvas, dx, dy, scale) {
                fill.color = c.cloudFill; canvas.drawPath(CLOUD, fill)
                stroke.color = c.lineInk; canvas.drawPath(CLOUD, stroke)
                stroke.alpha = (255 * .55f).toInt(); canvas.drawPath(FINE_CLOUD_LINE, stroke); stroke.alpha = 255
            }
        }
    }

    private inline fun puffs(canvas: Canvas, paint: () -> Paint) {
        for ((x, y, r) in PUFFS) canvas.drawCircle(x, y, r, paint())
        canvas.drawRoundRect(BASE, 2.45f, 2.45f, paint())
    }

    /** 3D cloud: one light from the upper left, a soft shade on each puff's edge and a bright rim along its top. */
    private fun sunlitCloud(canvas: Canvas, c: IconColors) {
        val body = radial(10.5f, 8.5f, 13f, intArrayOf(0xFFFFFFFF.toInt(), c.glowA, c.glowB), floatArrayOf(0f, .45f, 1f))
        for ((x, y, r) in PUFFS) {
            canvas.drawCircle(x, y, r, shade(body))
            // SVG: radial gradient at 42% / 38% of the puff's box, radius 62% of the box
            canvas.drawCircle(x, y, r, shade(radial(x - r + .84f * r, y - r + .76f * r, 1.24f * r, intArrayOf(alpha(c.glowB, 0f), alpha(c.glowB, 0f), alpha(c.glowB, .55f)), floatArrayOf(0f, .5f, 1f))))
        }
        canvas.drawRoundRect(BASE, 2.45f, 2.45f, shade(body))
        fill.shader = null
        stroke.color = 0xFFFFFFFF.toInt(); stroke.alpha = (255 * .95f).toInt(); stroke.strokeWidth = .75f
        for ((x, y, r) in PUFFS) {
            val rr = r - .75f
            canvas.drawArc(RectF(x - rr, y - rr, x + rr, y + rr), 205f, 80f, false, stroke)
        }
        stroke.alpha = 255; stroke.strokeWidth = lineWidth
    }

    private fun fog(canvas: Canvas, style: IconStyle, c: IconColors) {
        when (style) {
            IconStyle.BASIC -> {
                stroke.color = c.stroke
                canvas.drawLine(5f, 19f, 19f, 19f, stroke); canvas.drawLine(8f, 22f, 17f, 22f, stroke)
            }
            IconStyle.THREE_D -> {
                for (r in listOf(RectF(2.5f, 18.3f, 21.5f, 20.5f), RectF(5f, 21.4f, 19f, 23.6f))) {
                    val g = LinearGradient(r.left, 0f, r.right, 0f, intArrayOf(alpha(0xA9B7C8, 0f), 0xFFA9B7C8.toInt(), alpha(0xA9B7C8, 0f)), floatArrayOf(0f, .5f, 1f), Shader.TileMode.CLAMP)
                    canvas.drawRoundRect(r, 1.1f, 1.1f, shade(g))
                }
                fill.shader = null
            }
            IconStyle.FINE -> { stroke.color = c.lineInk; canvas.drawPath(FINE_FOG, stroke) }
        }
    }

    private fun drizzle(canvas: Canvas, style: IconStyle, c: IconColors) {
        when (style) {
            IconStyle.BASIC -> {
                stroke.color = c.rain
                canvas.drawLine(8f, 19f, 8f, 19.6f, stroke); canvas.drawLine(12f, 20f, 12f, 20.6f, stroke)
                canvas.drawLine(16f, 19f, 16f, 19.6f, stroke); canvas.drawLine(10f, 22.4f, 10f, 23f, stroke)
                canvas.drawLine(14f, 22.4f, 14f, 23f, stroke)
            }
            IconStyle.THREE_D -> {
                for ((x, y) in listOf(8f to 19.6f, 12f to 20.6f, 16f to 19.6f, 10f to 23f, 14f to 23f)) {
                    val d = drop(x, y, .5f); val b = RectF(); d.computeBounds(b, true)
                    canvas.drawPath(d, shade(vertical(b.top, b.bottom, 0xFFA8D3FF.toInt(), 0xFF2F74D6.toInt())))
                }
                fill.shader = null
            }
            IconStyle.FINE -> {
                stroke.color = c.rain
                stroke.pathEffect = DashPathEffect(floatArrayOf(.1f, 1.1f), 0f)
                canvas.drawPath(FINE_DRIZZLE, stroke)
                stroke.pathEffect = null
                for ((x, y) in listOf(8f to 19.4f, 12f to 20.6f, 16f to 19.4f, 10f to 22.7f, 14f to 22.7f)) canvas.drawCircle(x, y, .35f, stroke)
            }
        }
    }

    private fun rain(canvas: Canvas, style: IconStyle, c: IconColors) {
        when (style) {
            IconStyle.BASIC -> {
                stroke.color = c.rain
                canvas.drawLine(8.5f, 18.5f, 7.5f, 22f, stroke); canvas.drawLine(12.5f, 18.5f, 11.5f, 22f, stroke)
                canvas.drawLine(16.5f, 18.5f, 15.5f, 22f, stroke)
            }
            IconStyle.THREE_D -> {
                stroke.color = 0xFF000000.toInt(); stroke.strokeWidth = 1.9f
                stroke.shader = vertical(18f, 22.6f, 0xFFA8D3FF.toInt(), 0xFF2F74D6.toInt())
                for (x in listOf(8.6f, 12.6f, 16.6f)) canvas.drawLine(x, 18.4f, x - 1.1f, 22.2f, stroke)
                stroke.shader = null; stroke.strokeWidth = lineWidth
            }
            IconStyle.FINE -> {
                stroke.color = c.rain; fill.color = c.rain
                for (x in listOf(8.4f, 12.4f, 16.4f)) {
                    canvas.drawLine(x, 18.3f, x - 1f, 21.3f, stroke)
                    canvas.drawPath(drop(x - 1.4f, 22.7f, .42f), fill)
                }
            }
        }
    }

    private fun storm(canvas: Canvas, style: IconStyle, c: IconColors) {
        when (style) {
            IconStyle.BASIC -> { stroke.color = c.sun; canvas.drawPath(BOLT, stroke) }
            IconStyle.THREE_D -> {
                canvas.drawCircle(12.8f, 17.6f, 5f, shade(radial(12.8f, 17.6f, 5f, intArrayOf(alpha(0xFFD54A, .5f), alpha(0xFFD54A, .5f), alpha(0xFFD54A, 0f)), floatArrayOf(0f, .2f, 1f))))
                val b = RectF(); GLOW_BOLT.computeBounds(b, true)
                canvas.drawPath(GLOW_BOLT, shade(vertical(b.top, b.bottom, 0xFFFFF1A6.toInt(), 0xFFFFA514.toInt())))
                fill.shader = null
            }
            IconStyle.FINE -> { stroke.color = c.sun; stroke.strokeWidth = 1.3f; canvas.drawPath(FINE_BOLT, stroke); stroke.strokeWidth = lineWidth }
        }
    }
}
