package com.prostellis.awe.widget

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import androidx.core.graphics.PathParser
import com.prostellis.awe.weather.Cat
import kotlin.math.cos
import kotlin.math.sin

/** Colors the weather icons use (the web app's --ink-2, --sun-ink, … variables). */
data class IconColors(
    val stroke: Int, val ink3: Int, val sun: Int, val cloudFill: Int, val rain: Int,
    val moonInk: Int, val moonIcon: Int, val crater: Int,
)

/** The web app's inline-SVG weather icons, drawn on a 24×24 grid with the same shapes and stroke. */
object WeatherIcons {
    private val CLOUD: Path = PathParser.createPathFromPathData("M7 19a4 4 0 0 1-.5-7.97A5.5 5.5 0 0 1 17 10a4.5 4.5 0 0 1 0 9z")
    private val CRESCENT: Path = PathParser.createPathFromPathData("M19.5 14.5A8 8 0 1 1 9.5 4.5a6.5 6.5 0 0 0 10 10z")
    private val BOLT: Path = PathParser.createPathFromPathData("M12.5 15.5l-2.5 4h3l-1.5 3.5")

    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeWidth = 1.7f; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND
    }
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }

    /** Draws the icon for a category with its top-left at (x, y), [size] wide. */
    fun draw(canvas: Canvas, cat: Cat, isDay: Boolean, x: Float, y: Float, size: Float, c: IconColors, shadow: Boolean = false) {
        canvas.save()
        canvas.translate(x, y)
        canvas.scale(size / 24f, size / 24f)
        if (shadow) {
            stroke.setShadowLayer(1.2f, 0f, .6f, 0x55000000); fill.setShadowLayer(1.2f, 0f, .6f, 0x55000000)
        } else {
            stroke.clearShadowLayer(); fill.clearShadowLayer()
        }
        stroke.strokeWidth = 1.7f
        when (cat) {
            Cat.CLEAR -> if (isDay) sun(canvas, 12f, 12f, 4.2f, 2.6f, c) else craterMoon(canvas, c)
            Cat.PARTLY -> {
                if (isDay) sun(canvas, 8.5f, 8f, 2.8f, 1.6f, c)
                else { canvas.save(); canvas.translate(-3f, -3f); canvas.scale(.62f, .62f); crescent(canvas, c); canvas.restore() }
                cloud(canvas, c, 3f, 3f, .86f)
            }
            Cat.CLOUDY -> cloud(canvas, c)
            Cat.FOG -> {
                cloud(canvas, c, 0f, -4f)
                line(canvas, 5f, 19f, 19f, 19f, c.stroke); line(canvas, 8f, 22f, 17f, 22f, c.stroke)
            }
            Cat.DRIZZLE -> {
                cloud(canvas, c, 0f, -3f)
                line(canvas, 8f, 19f, 8f, 19.6f, c.rain); line(canvas, 12f, 20f, 12f, 20.6f, c.rain)
                line(canvas, 16f, 19f, 16f, 19.6f, c.rain); line(canvas, 10f, 22.4f, 10f, 23f, c.rain)
                line(canvas, 14f, 22.4f, 14f, 23f, c.rain)
            }
            Cat.RAIN -> {
                cloud(canvas, c, 0f, -3f)
                line(canvas, 8.5f, 18.5f, 7.5f, 22f, c.rain); line(canvas, 12.5f, 18.5f, 11.5f, 22f, c.rain)
                line(canvas, 16.5f, 18.5f, 15.5f, 22f, c.rain)
            }
            Cat.SNOW -> {
                cloud(canvas, c, 0f, -3f)
                stroke.color = c.rain
                for ((cx, cy) in listOf(8f to 19.5f, 12f to 21f, 16f to 19.5f, 10f to 22.8f, 14f to 22.8f)) canvas.drawCircle(cx, cy, .5f, stroke)
            }
            Cat.STORM -> {
                cloud(canvas, c, 0f, -3f)
                stroke.color = c.sun
                canvas.drawPath(BOLT, stroke)
            }
        }
        canvas.restore()
    }

    private fun line(canvas: Canvas, x1: Float, y1: Float, x2: Float, y2: Float, color: Int) {
        stroke.color = color
        canvas.drawLine(x1, y1, x2, y2, stroke)
    }

    private fun sun(canvas: Canvas, cx: Float, cy: Float, r: Float, ray: Float, c: IconColors) {
        fill.color = c.sun; fill.alpha = (255 * .25f).toInt()
        canvas.drawCircle(cx, cy, r, fill)
        stroke.color = c.sun
        canvas.drawCircle(cx, cy, r, stroke)
        for (k in 0 until 8) {
            val a = k * Math.PI / 4
            val r1 = r + 2; val r2 = r + 2 + ray
            canvas.drawLine(
                (cx + cos(a) * r1).toFloat(), (cy + sin(a) * r1).toFloat(),
                (cx + cos(a) * r2).toFloat(), (cy + sin(a) * r2).toFloat(), stroke,
            )
        }
    }

    private fun cloud(canvas: Canvas, c: IconColors, dx: Float = 0f, dy: Float = 0f, scale: Float = 1f) {
        canvas.save()
        canvas.translate(dx, dy)
        canvas.scale(scale, scale)
        fill.color = c.cloudFill
        canvas.drawPath(CLOUD, fill)
        stroke.color = c.stroke
        canvas.drawPath(CLOUD, stroke)
        canvas.restore()
    }

    private fun craterMoon(canvas: Canvas, c: IconColors) {
        fill.color = c.moonIcon
        canvas.drawCircle(12f, 12f, 7.5f, fill)
        stroke.color = c.ink3; stroke.strokeWidth = 1.4f
        canvas.drawCircle(12f, 12f, 7.5f, stroke)
        stroke.strokeWidth = 1.7f
        fill.color = c.crater
        canvas.drawCircle(9.4f, 9.8f, 1.7f, fill); canvas.drawCircle(14.6f, 13.4f, 1.3f, fill)
        canvas.drawCircle(11f, 15.4f, .9f, fill); canvas.drawCircle(14.2f, 8.6f, .7f, fill)
    }

    private fun crescent(canvas: Canvas, c: IconColors) {
        fill.color = c.moonInk
        canvas.drawPath(CRESCENT, fill)
        stroke.color = c.stroke
        canvas.drawPath(CRESCENT, stroke)
    }
}
