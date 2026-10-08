package com.prostellis.awe.widget

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.Intent
import android.graphics.LinearGradient
import android.graphics.Shader
import android.os.Bundle
import android.widget.ImageView
import android.widget.RadioGroup
import android.widget.SeekBar
import android.widget.TextView
import com.prostellis.awe.MainActivity
import com.prostellis.awe.R
import com.prostellis.awe.data.RefreshMode
import com.prostellis.awe.data.Store
import com.prostellis.awe.data.WidgetSettings
import kotlin.math.min

/**
 * Settings for one widget, opened from its gear button. The real wallpaper shows behind the
 * preview, so the transparency slider shows exactly what the home screen will look like.
 */
class WidgetSettingsActivity : Activity() {

    private var widgetId = AppWidgetManager.INVALID_APPWIDGET_ID
    private lateinit var kind: WidgetKind
    private lateinit var store: Store
    private var transparency = 0
    private var refresh = RefreshMode.EVERY_30
    private var showCurrent = true

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

        setContentView(R.layout.activity_widget_settings)
        fitSystemBars()
        findViewById<TextView>(R.id.title).setText(
            if (kind == WidgetKind.SMALL) R.string.settings_title_small else R.string.settings_title_forecast,
        )
        paintWordmark(findViewById(R.id.wordmark_weather))
        findViewById<android.view.View>(R.id.back).setOnClickListener { finish() }

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

        // The current-weather switch only applies to the forecast widget
        findViewById<android.view.View>(R.id.show_current_card).visibility =
            if (kind == WidgetKind.FORECAST) android.view.View.VISIBLE else android.view.View.GONE
        findViewById<android.widget.Switch>(R.id.show_current).apply {
            isChecked = showCurrent
            setOnCheckedChangeListener { _, on -> showCurrent = on; drawPreview() }
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
        findViewById<android.view.View>(R.id.location_row).setOnClickListener {
            save()
            startActivity(
                Intent(this, MainActivity::class.java)
                    .putExtra(MainActivity.EXTRA_OPEN_LOCATION, true)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            )
            finish()
        }
        findViewById<android.view.View>(R.id.done).setOnClickListener { save(); finish() }

        findViewById<ImageView>(R.id.preview).post { drawPreview() }
    }

    /** Header below the status bar, buttons above the navigation bar. */
    private fun fitSystemBars() {
        val header = findViewById<android.view.View>(R.id.header)
        val body = findViewById<android.view.View>(R.id.body)
        val headerTop = header.paddingTop
        findViewById<android.view.View>(R.id.root).setOnApplyWindowInsetsListener { _, insets ->
            val bars = insets.getInsets(android.view.WindowInsets.Type.systemBars())
            header.setPadding(header.paddingLeft, headerTop + bars.top, header.paddingRight, header.paddingBottom)
            body.setPadding(0, 0, 0, bars.bottom)
            android.view.WindowInsets.CONSUMED
        }
    }

    private fun drawPreview() {
        val preview = findViewById<ImageView>(R.id.preview)
        val (w, h) = WidgetUpdater.sizeDp(this, widgetId, kind)
        val r = WidgetUpdater.renderFor(this, kind, w, h, transparency, showCurrent)
        val density = resources.displayMetrics.density
        // Real size when it fits; otherwise scaled down to the screen width.
        val container = findViewById<android.view.View>(R.id.preview_container)
        val maxW = container.width - container.paddingLeft - container.paddingRight
        val scale = if (maxW > 0) min(1f, maxW / (w * density)) else 1f
        preview.layoutParams = preview.layoutParams.apply {
            width = (w * density * scale).toInt(); height = (h * density * scale).toInt()
        }
        preview.setImageBitmap(r.bitmap)
        findViewById<ImageView>(R.id.preview_refresh).setColorFilter(r.buttonColor)
        findViewById<ImageView>(R.id.preview_gear).setColorFilter(r.buttonColor)
        placeButton(R.id.preview_refresh, r.refresh, scale)
        placeButton(R.id.preview_gear, r.gear, scale)
    }

    private fun placeButton(id: Int, spot: ButtonSpot, scale: Float) {
        val d = resources.displayMetrics.density * scale
        val v = findViewById<ImageView>(id)
        val size = (spot.iconSize * d).toInt()
        v.layoutParams = (v.layoutParams as android.widget.FrameLayout.LayoutParams).apply {
            width = size; height = size
            leftMargin = ((spot.centerX - spot.iconSize / 2) * d).toInt()
            topMargin = ((spot.centerY - spot.iconSize / 2) * d).toInt()
        }
    }

    private fun save() {
        store.saveWidgetSettings(widgetId, WidgetSettings(transparency, refresh, showCurrent))
        WidgetUpdater.update(this, widgetId)
        Refresh.schedule(this)
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
