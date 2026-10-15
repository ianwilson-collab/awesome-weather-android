package com.prostellis.awe.widget

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.drawable.Drawable
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import kotlin.math.min

/** A color as a circle (or a rounded square): Auto shows its two picks, white and near-black, split corner to corner. */
class SwatchDrawable(private val color: ClockColor, private val outline: Int, private val round: Boolean = true) : Drawable() {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)

    override fun draw(canvas: Canvas) {
        val b = RectF(bounds)
        val shape = Path().apply {
            if (round) addOval(b, Path.Direction.CW) else addRoundRect(b, b.height() * .28f, b.height() * .28f, Path.Direction.CW)
        }
        canvas.save()
        canvas.clipPath(shape)
        val stops = ClockPalette.stops(color)
        when {
            stops == null -> {
                paint.shader = null
                paint.color = 0xFFFFFFFF.toInt(); canvas.drawRect(b, paint)
                paint.color = 0xFF141A22.toInt()
                canvas.drawPath(Path().apply { moveTo(b.right, b.top); lineTo(b.right, b.bottom); lineTo(b.left, b.bottom); close() }, paint)
            }
            stops.size == 1 -> { paint.shader = null; paint.color = stops[0]; canvas.drawRect(b, paint) }
            else -> {
                paint.shader = LinearGradient(0f, b.top, 0f, b.bottom, stops, ClockPalette.positions(color), Shader.TileMode.CLAMP)
                canvas.drawRect(b, paint)
            }
        }
        canvas.restore()
        paint.shader = null
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 1.5f
        paint.color = outline
        canvas.drawPath(shape, paint)
        paint.style = Paint.Style.FILL
    }

    override fun setAlpha(alpha: Int) = Unit
    override fun setColorFilter(colorFilter: ColorFilter?) = Unit
    @Deprecated("Deprecated in Java")
    override fun getOpacity() = PixelFormat.TRANSLUCENT
}

/** The picker's square: saturation left to right, brightness bottom to top, for the hue picked on the strip. Drag or use the arrow keys. */
class SvSquareView(context: Context) : View(context) {
    var hue = 0f
        set(v) { field = v; invalidate() }
    var sat = 1f
    var value = 1f
    var onChange: ((Float, Float) -> Unit)? = null
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val d = resources.displayMetrics.density

    init { isFocusable = true }

    fun set(s: Float, v: Float) { sat = s; value = v; invalidate() }

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat(); val h = height.toFloat(); val r = 10f * d
        val box = Path().apply { addRoundRect(RectF(0f, 0f, w, h), r, r, Path.Direction.CW) }
        canvas.save()
        canvas.clipPath(box)
        paint.shader = LinearGradient(0f, 0f, w, 0f, 0xFFFFFFFF.toInt(), Color.HSVToColor(floatArrayOf(hue, 1f, 1f)), Shader.TileMode.CLAMP)
        canvas.drawRect(0f, 0f, w, h, paint)
        paint.shader = LinearGradient(0f, 0f, 0f, h, 0x00000000, 0xFF000000.toInt(), Shader.TileMode.CLAMP)
        canvas.drawRect(0f, 0f, w, h, paint)
        canvas.restore()
        paint.shader = null
        ring(canvas, paint, sat * w, (1 - value) * h, d)
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(e: MotionEvent): Boolean {
        parent?.requestDisallowInterceptTouchEvent(true)
        set((e.x / width).coerceIn(0f, 1f), 1f - (e.y / height).coerceIn(0f, 1f))
        onChange?.invoke(sat, value)
        return true
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        val (dx, dy) = arrow(keyCode) ?: return super.onKeyDown(keyCode, event)
        set((sat + dx * .05f).coerceIn(0f, 1f), (value - dy * .05f).coerceIn(0f, 1f))
        onChange?.invoke(sat, value)
        return true
    }
}

/** The picker's hue strip, top to bottom. Drag or use the arrow keys. */
class HueStripView(context: Context) : View(context) {
    var hue = 0f
        set(v) { field = v; invalidate() }
    var onChange: ((Float) -> Unit)? = null
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val d = resources.displayMetrics.density

    init { isFocusable = true }

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat(); val h = height.toFloat(); val r = min(w / 2, 10f * d)
        val hues = IntArray(7) { Color.HSVToColor(floatArrayOf(it * 60f % 360f, 1f, 1f)) }
        paint.shader = LinearGradient(0f, 0f, 0f, h, hues, null, Shader.TileMode.CLAMP)
        canvas.drawRoundRect(RectF(0f, 0f, w, h), r, r, paint)
        paint.shader = null
        ring(canvas, paint, w / 2, hue / 360f * h, d)
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(e: MotionEvent): Boolean {
        parent?.requestDisallowInterceptTouchEvent(true)
        hue = (e.y / height).coerceIn(0f, 1f) * 360f
        onChange?.invoke(hue)
        return true
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        val (dx, dy) = arrow(keyCode) ?: return super.onKeyDown(keyCode, event)
        hue = (hue + (dy + dx) * 10f + 360f) % 360f
        onChange?.invoke(hue)
        return true
    }
}

/** Arrow keys as steps: right and down are positive. */
private fun arrow(keyCode: Int): Pair<Int, Int>? = when (keyCode) {
    KeyEvent.KEYCODE_DPAD_LEFT -> -1 to 0
    KeyEvent.KEYCODE_DPAD_RIGHT -> 1 to 0
    KeyEvent.KEYCODE_DPAD_UP -> 0 to -1
    KeyEvent.KEYCODE_DPAD_DOWN -> 0 to 1
    else -> null
}

/** The marker on the square and the strip: a white ring with a dark edge, readable on any color. */
private fun ring(canvas: Canvas, paint: Paint, x: Float, y: Float, d: Float) {
    paint.style = Paint.Style.STROKE
    paint.strokeWidth = 4f * d
    paint.color = 0x66000000
    canvas.drawCircle(x, y, 8f * d, paint)
    paint.strokeWidth = 2.5f * d
    paint.color = 0xFFFFFFFF.toInt()
    canvas.drawCircle(x, y, 8f * d, paint)
    paint.style = Paint.Style.FILL
}
