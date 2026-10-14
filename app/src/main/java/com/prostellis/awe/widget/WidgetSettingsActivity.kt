package com.prostellis.awe.widget

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Shader
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.SeekBar
import android.widget.TextView
import com.prostellis.awe.MainActivity
import com.prostellis.awe.R
import com.prostellis.awe.data.RefreshMode
import com.prostellis.awe.data.Store
import com.prostellis.awe.data.WidgetSettings
import com.prostellis.awe.weather.Cat
import kotlin.math.min

/**
 * The one settings sheet every AWE widget shares. It opens when a widget is placed, by long-pressing it,
 * and from the forecast widget's gear button. The real wallpaper shows behind the preview, so the
 * sliders show exactly what the home screen will look like.
 */
class WidgetSettingsActivity : Activity() {

    private var widgetId = AppWidgetManager.INVALID_APPWIDGET_ID
    private lateinit var kind: WidgetKind
    private lateinit var store: Store
    private var transparency = 0
    private var refresh = RefreshMode.EVERY_30
    private var showCurrent = true
    private var tempColor = 0
    private var glow = Glow.DEFAULT
    private var iconStyle = IconStyle.GLOW

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        widgetId = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
        val k = WidgetUpdater.kindOf(this, widgetId)
        if (k == null) { finish(); return }
        kind = k
        // Leaving without "Done" keeps the widget as it was (never removes it).
        setResult(RESULT_OK, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId))

        store = Store(this)
        val saved = store.widgetSettings(widgetId)
        transparency = saved.transparency
        refresh = saved.refresh
        showCurrent = saved.showCurrent
        tempColor = saved.tempColor
        glow = saved.glow
        iconStyle = store.iconStyle

        setContentView(R.layout.activity_widget_settings)
        fitSystemBars()
        findViewById<TextView>(R.id.title).setText(
            when (kind) {
                WidgetKind.TINY -> R.string.settings_title_tiny
                WidgetKind.DAILY -> R.string.settings_title_daily
                WidgetKind.HOURLY -> R.string.settings_title_hourly
                WidgetKind.FORECAST -> R.string.settings_title_forecast
            },
        )
        paintWordmark(findViewById(R.id.wordmark_weather))
        findViewById<View>(R.id.back).setOnClickListener { finish() }

        val value = findViewById<TextView>(R.id.transparency_value)
        val seek = findViewById<SeekBar>(R.id.transparency)
        seek.max = 20
        seek.progress = transparency / 5
        value.text = getString(R.string.percent, transparency)
        seek.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(bar: SeekBar, progress: Int, fromUser: Boolean) {
                transparency = progress * 5
                value.text = getString(R.string.percent, transparency)
                drawPreview()
            }
            override fun onStartTrackingTouch(bar: SeekBar) = Unit
            override fun onStopTrackingTouch(bar: SeekBar) = Unit
        })

        setUpTempColor()
        setUpGlow()

        // The current weather can be turned off only on the forecast widget; on the others it's what the widget shows
        val forecast = kind == WidgetKind.FORECAST
        findViewById<android.widget.Switch>(R.id.show_current).apply {
            isChecked = showCurrent || !forecast
            isEnabled = forecast
            setOnCheckedChangeListener { _, on -> showCurrent = on; drawPreview() }
        }
        findViewById<TextView>(R.id.show_current_help).setText(if (forecast) R.string.settings_show_current_help else R.string.settings_show_current_always)

        // Icon style: shared by every widget and the app; each choice shows a few of its icons
        val styleButtons = mapOf(R.id.style_basic to IconStyle.BASIC, R.id.style_glow to IconStyle.GLOW, R.id.style_fine to IconStyle.FINE)
        for ((id, style) in styleButtons) {
            findViewById<RadioButton>(id).setCompoundDrawablesRelativeWithIntrinsicBounds(null, null, iconSample(style), null)
        }
        val styleGroup = findViewById<RadioGroup>(R.id.icon_style_group)
        styleGroup.check(styleButtons.entries.first { it.value == iconStyle }.key)
        styleGroup.setOnCheckedChangeListener { _, checked ->
            iconStyle = styleButtons[checked] ?: IconStyle.GLOW
            drawPreview()
        }

        val group = findViewById<RadioGroup>(R.id.refresh_group)
        group.check(
            when (refresh) {
                RefreshMode.EVERY_30 -> R.id.refresh_30
                RefreshMode.EVERY_60 -> R.id.refresh_60
                RefreshMode.MANUAL -> R.id.refresh_manual
            },
        )
        group.setOnCheckedChangeListener { _, checked ->
            refresh = when (checked) {
                R.id.refresh_60 -> RefreshMode.EVERY_60
                R.id.refresh_manual -> RefreshMode.MANUAL
                else -> RefreshMode.EVERY_30
            }
        }

        findViewById<TextView>(R.id.location_value).text = store.place.shortLabel
        findViewById<View>(R.id.location_row).setOnClickListener {
            save()
            startActivity(
                Intent(this, MainActivity::class.java)
                    .putExtra(MainActivity.EXTRA_OPEN_LOCATION, true)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            )
            finish()
        }
        findViewById<View>(R.id.done).setOnClickListener { save(); finish() }

        findViewById<ImageView>(R.id.preview).post { drawPreview() }
    }

    /** Header below the status bar, buttons above the navigation bar. */
    private fun fitSystemBars() {
        val header = findViewById<View>(R.id.header)
        val body = findViewById<View>(R.id.body)
        val headerTop = header.paddingTop
        findViewById<View>(R.id.root).setOnApplyWindowInsetsListener { _, insets ->
            val bars = insets.getInsets(android.view.WindowInsets.Type.systemBars())
            header.setPadding(header.paddingLeft, headerTop + bars.top, header.paddingRight, header.paddingBottom)
            body.setPadding(0, 0, 0, bars.bottom)
            android.view.WindowInsets.CONSUMED
        }
    }

    /** Temperature color: a slider through Auto and ten colors, with the same choices as swatches underneath. */
    private fun setUpTempColor() {
        val seek = findViewById<SeekBar>(R.id.temp_color)
        val value = findViewById<TextView>(R.id.temp_color_value)
        val row = findViewById<LinearLayout>(R.id.temp_swatches)
        if (kind == WidgetKind.TINY) findViewById<TextView>(R.id.temp_color_help).setText(R.string.settings_temp_color_help_tiny)
        val d = resources.displayMetrics.density
        val swatches = TempColors.CHOICES.mapIndexed { i, (name, color) ->
            View(this).apply {
                contentDescription = name
                background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    // Auto shows both of its picks: white and near-black
                    if (color == null) { colors = intArrayOf(0xFFFFFFFF.toInt(), 0xFF141A22.toInt()); orientation = GradientDrawable.Orientation.TL_BR }
                    else setColor(color)
                }
                setOnClickListener { seek.progress = i }
                row.addView(this, LinearLayout.LayoutParams(0, (22 * d).toInt(), 1f).apply { marginStart = (3 * d).toInt(); marginEnd = (3 * d).toInt() })
            }
        }
        fun show() {
            value.text = TempColors.CHOICES[tempColor].first
            swatches.forEachIndexed { i, v ->
                (v.background as GradientDrawable).setStroke((if (i == tempColor) 3 * d else 1 * d).toInt(), getColor(if (i == tempColor) R.color.accent else R.color.line))
            }
        }
        seek.max = TempColors.CHOICES.size - 1
        tempColor = tempColor.coerceIn(0, seek.max)
        seek.progress = tempColor
        show()
        seek.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(bar: SeekBar, progress: Int, fromUser: Boolean) {
                tempColor = progress
                show()
                drawPreview()
            }
            override fun onStartTrackingTouch(bar: SeekBar) = Unit
            override fun onStopTrackingTouch(bar: SeekBar) = Unit
        })
    }

    /** Glow strength: Off to Max, for the halo behind this widget's temperatures. */
    private fun setUpGlow() {
        val seek = findViewById<SeekBar>(R.id.glow)
        val value = findViewById<TextView>(R.id.glow_value)
        seek.max = Glow.NAMES.size - 1
        glow = glow.coerceIn(0, seek.max)
        seek.progress = glow
        value.text = Glow.NAMES[glow]
        seek.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(bar: SeekBar, progress: Int, fromUser: Boolean) {
                glow = progress
                value.text = Glow.NAMES[glow]
                drawPreview()
            }
            override fun onStartTrackingTouch(bar: SeekBar) = Unit
            override fun onStopTrackingTouch(bar: SeekBar) = Unit
        })
    }

    private fun settings() = WidgetSettings(transparency, refresh, showCurrent, tempColor, glow)

    private fun drawPreview() {
        val preview = findViewById<ImageView>(R.id.preview)
        val (w, h) = WidgetUpdater.sizeDp(this, widgetId, kind)
        val r = WidgetUpdater.renderFor(this, kind, w, h, settings(), iconStyle)
        val density = resources.displayMetrics.density
        // Real size when it fits; otherwise scaled down to the screen width.
        val container = findViewById<View>(R.id.preview_container)
        val maxW = container.width - container.paddingLeft - container.paddingRight
        val scale = if (maxW > 0) min(1f, maxW / (w * density)) else 1f
        preview.layoutParams = preview.layoutParams.apply {
            width = (w * density * scale).toInt(); height = (h * density * scale).toInt()
        }
        preview.setImageBitmap(r.bitmap)
        placeButton(R.id.preview_refresh, r.refresh, r.buttonColor, scale)
        placeButton(R.id.preview_gear, r.gear, r.buttonColor, scale)
    }

    /** The buttons on the preview: refresh on every widget, settings on the forecast widget only. */
    private fun placeButton(id: Int, spot: ButtonSpot?, color: Int, scale: Float) {
        val d = resources.displayMetrics.density * scale
        val v = findViewById<ImageView>(id)
        v.visibility = if (spot == null) View.GONE else View.VISIBLE
        if (spot == null) return
        v.setColorFilter(color)
        val size = (spot.iconSize * d).toInt()
        v.layoutParams = (v.layoutParams as android.widget.FrameLayout.LayoutParams).apply {
            width = size; height = size
            leftMargin = ((spot.centerX - spot.iconSize / 2) * d).toInt()
            topMargin = ((spot.centerY - spot.iconSize / 2) * d).toInt()
        }
    }

    private fun save() {
        store.saveWidgetSettings(widgetId, settings())
        if (iconStyle != store.iconStyle) {
            store.iconStyle = iconStyle
            WidgetUpdater.updateAll(this)
        } else {
            WidgetUpdater.update(this, widgetId)
        }
        Refresh.schedule(this)
    }

    /** Five of the style's icons in a row, in the phone's light or dark colors. */
    private fun iconSample(style: IconStyle): BitmapDrawable {
        val d = resources.displayMetrics.density
        val size = 24f; val gap = 7f
        val cats = listOf(Cat.CLEAR, Cat.PARTLY, Cat.RAIN, Cat.SNOW, Cat.STORM)
        val bmp = Bitmap.createBitmap(((cats.size * size + (cats.size - 1) * gap) * d).toInt(), (size * d).toInt(), Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        canvas.scale(d, d)
        val colors = if (WidgetUpdater.phoneIsDark(this)) WidgetTheme.DARK.icons else WidgetTheme.LIGHT.icons
        cats.forEachIndexed { i, cat -> WeatherIcons.draw(canvas, cat, true, i * (size + gap), 0f, size, colors, style) }
        return BitmapDrawable(resources, bmp)
    }

    /** "Weather" in the ProStellis green → blue fade, top-left to bottom-right. */
    private fun paintWordmark(view: TextView) {
        view.post {
            view.paint.shader = LinearGradient(
                0f, 0f, view.width.toFloat(), view.height.toFloat(),
                getColor(R.color.awe_green), getColor(R.color.awe_blue), Shader.TileMode.CLAMP,
            )
            view.invalidate()
        }
    }
}
