package com.prostellis.awe.widget

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.Intent
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import android.os.Bundle
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.GridLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.RadioGroup
import android.widget.SeekBar
import android.widget.Switch
import android.widget.TextView
import com.prostellis.awe.R
import com.prostellis.awe.data.Store
import kotlin.math.min

/**
 * AWE Clock settings: a live preview on the real wallpaper, then the time format and style, the three colors
 * (each through the same picker), glow, transparency, the weather on the right, the day and date, and the weather
 * line. Opens when the clock is placed and by long-pressing it.
 */
class ClockSettingsActivity : Activity() {

    private var widgetId = AppWidgetManager.INVALID_APPWIDGET_ID
    private lateinit var store: Store
    private var settings = ClockSettings()
    private val d by lazy { resources.displayMetrics.density }

    /** The three colors the picker sets. The date's color also colors the weather line. */
    private enum class Target { TIME, TEMP, DATE }
    private lateinit var picker: ColorPicker
    private var picking = Target.TIME

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        widgetId = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
        if (!ClockUpdater.isClock(this, widgetId)) { finish(); return }
        // Leaving without "Done" keeps the clock as it was (never removes it).
        setResult(RESULT_OK, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId))
        store = Store(this)
        settings = store.clockSettings(widgetId)

        setContentView(R.layout.activity_clock_settings)
        fitSystemBars()
        paintWordmark(findViewById(R.id.wordmark_weather))
        findViewById<View>(R.id.back).setOnClickListener { finish() }

        setUpFormat()
        setUpFaces()
        setUpSlider(R.id.glow, R.id.glow_value, Glow.NAMES.size - 1, { settings.glow }, { Glow.NAMES[it] }) { settings = settings.copy(glow = it) }
        setUpSlider(R.id.transparency, R.id.transparency_value, 20, { settings.transparency / 5 }, { getString(R.string.percent, it * 5) }) {
            settings = settings.copy(transparency = it * 5)
        }
        picker = ColorPicker(this, findViewById(R.id.body), findViewById(R.id.picker), findViewById(R.id.picker_body),
            onPreview = { c -> drawPreview(withColor(picking, c)) },
            onClose = { c -> settings = withColor(picking, c); paintColorRows(); drawPreview() })
        colorRow(R.id.row_time_color, Target.TIME)
        colorRow(R.id.row_temp_color, Target.TEMP)
        colorRow(R.id.row_date_color, Target.DATE)
        switch(R.id.weather, settings.weather) { settings = settings.copy(weather = it) }
        switch(R.id.hilo, settings.hiLo) { settings = settings.copy(hiLo = it) }
        switch(R.id.date, settings.date) { settings = settings.copy(date = it) }
        switch(R.id.wxline, settings.wxLine) { settings = settings.copy(wxLine = it) }
        switch(R.id.wx_same, settings.wxSame) { settings = settings.copy(wxSame = it) }
        aboveBelow(R.id.date_pos_group, R.id.date_above, R.id.date_below, settings.datePos) { settings = settings.copy(datePos = it) }
        aboveBelow(R.id.wx_pos_group, R.id.wx_above, R.id.wx_below, settings.wxPos) { settings = settings.copy(wxPos = it) }
        aboveBelow(R.id.wx_order_group, R.id.wx_order_above, R.id.wx_order_below, settings.wxOrder) { settings = settings.copy(wxOrder = it) }
        findViewById<View>(R.id.done).setOnClickListener { save(); finish() }

        showDependentRows()
        findViewById<ImageView>(R.id.preview).post { drawPreview() }
    }

    /** Header below the status bar, buttons above the navigation bar. */
    private fun fitSystemBars() {
        val header = findViewById<View>(R.id.header)
        val body = findViewById<View>(R.id.body)
        val picker = findViewById<View>(R.id.picker)
        val headerTop = header.paddingTop
        findViewById<View>(R.id.root).setOnApplyWindowInsetsListener { _, insets ->
            val bars = insets.getInsets(android.view.WindowInsets.Type.systemBars())
            header.setPadding(header.paddingLeft, headerTop + bars.top, header.paddingRight, header.paddingBottom)
            body.setPadding(0, 0, 0, bars.bottom)
            picker.setPadding(0, 0, 0, bars.bottom)
            android.view.WindowInsets.CONSUMED
        }
    }

    private fun changed() {
        showDependentRows()
        paintColorRows()
        drawPreview()
    }

    /**
     * High and low and the temperature color show only with the weather on; position and date color only with the date on.
     * The weather line's settings: Same line as date (with the date on), its position (unless it's on the date's line),
     * and which side of the date (when both are on the same side of the clock). One color row serves both lines.
     */
    private fun showDependentRows() {
        fun show(id: Int, on: Boolean) { findViewById<View>(id).visibility = if (on) View.VISIBLE else View.GONE }
        val s = settings
        show(R.id.hilo_row, s.weather)
        show(R.id.row_temp_color, s.weather)
        show(R.id.date_pos_row, s.date)
        show(R.id.row_date_color, s.date || s.wxLine)
        show(R.id.wx_same_row, s.wxLine && s.date)
        show(R.id.wx_pos_row, s.wxLine && !(s.date && s.wxSame))
        show(R.id.wx_order_row, s.wxLine && s.date && !s.wxSame && s.datePos == s.wxPos)
    }

    private fun aboveBelow(groupId: Int, aboveId: Int, belowId: Int, current: DatePos, set: (DatePos) -> Unit) {
        findViewById<RadioGroup>(groupId).apply {
            check(if (current == DatePos.ABOVE) aboveId else belowId)
            setOnCheckedChangeListener { _, id -> set(if (id == aboveId) DatePos.ABOVE else DatePos.BELOW); changed() }
        }
    }

    private fun setUpFormat() {
        val ids = mapOf(TimeFormat.PHONE to R.id.format_phone, TimeFormat.H12 to R.id.format_12, TimeFormat.H24 to R.id.format_24)
        findViewById<RadioGroup>(R.id.format_group).apply {
            check(ids.getValue(settings.format))
            setOnCheckedChangeListener { _, id -> settings = settings.copy(format = ids.entries.first { it.value == id }.key); changed() }
        }
    }

    /** The ten styles, each showing "12:48" in its own font (just "12" where the face is too wide for the button). */
    private fun setUpFaces() {
        val grid = findViewById<GridLayout>(R.id.faces)
        val ink = getColor(R.color.ink)
        ClockFace.ALL.forEach { face ->
            val type = face.typeface(this)
            val measure = Paint().apply { typeface = type; textSize = 16f; letterSpacing = face.track; face.variation?.let { fontVariationSettings = it } }
            val sample = if (measure.measureText("12:48") > 52f) "12" else "12:48"
            LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                background = getDrawable(R.drawable.face_button)
                setPadding(0, (8 * d).toInt(), 0, (6 * d).toInt())
                contentDescription = face.name
                isFocusable = true
                addView(TextView(context).apply {
                    text = sample; typeface = type; letterSpacing = face.track
                    face.variation?.let { fontVariationSettings = it }
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f); setTextColor(ink); maxLines = 1
                    importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
                })
                addView(TextView(context).apply {
                    text = face.name; typeface = resources.getFont(R.font.dm_sans)
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 10f); setTextColor(getColor(R.color.ink3)); maxLines = 1
                    importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
                })
                setOnClickListener { settings = settings.copy(face = face.id); markFaces(grid); drawPreview() }
                grid.addView(this, GridLayout.LayoutParams(GridLayout.spec(GridLayout.UNDEFINED), GridLayout.spec(GridLayout.UNDEFINED, 1f)).apply {
                    width = 0; setMargins((3 * d).toInt(), (3 * d).toInt(), (3 * d).toInt(), (3 * d).toInt())
                })
            }
        }
        markFaces(grid)
    }

    private fun markFaces(grid: GridLayout) {
        for (i in 0 until grid.childCount) grid.getChildAt(i).isSelected = ClockFace.ALL[i].id == ClockFace.byId(settings.face).id
    }

    private fun setUpSlider(seekId: Int, valueId: Int, max: Int, get: () -> Int, label: (Int) -> String, set: (Int) -> Unit) {
        val value = findViewById<TextView>(valueId)
        findViewById<SeekBar>(seekId).apply {
            this.max = max
            progress = get().coerceIn(0, max)
            value.text = label(progress)
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(bar: SeekBar, p: Int, fromUser: Boolean) { set(p); value.text = label(p); drawPreview() }
                override fun onStartTrackingTouch(bar: SeekBar) = Unit
                override fun onStopTrackingTouch(bar: SeekBar) = Unit
            })
        }
    }

    private fun switch(id: Int, on: Boolean, set: (Boolean) -> Unit) {
        findViewById<Switch>(id).apply { isChecked = on; setOnCheckedChangeListener { _, v -> set(v); changed() } }
    }

    /* ---------------- Colors ---------------- */

    private fun colorOf(target: Target, s: ClockSettings = settings) = when (target) {
        Target.TIME -> s.timeColor; Target.TEMP -> s.tempColor; Target.DATE -> s.dateColor
    }

    private fun withColor(target: Target, c: ClockColor) = when (target) {
        Target.TIME -> settings.copy(timeColor = c); Target.TEMP -> settings.copy(tempColor = c); Target.DATE -> settings.copy(dateColor = c)
    }

    /** The date's color row is named for the weather line too while that's on. */
    private fun title(target: Target) = getString(when (target) {
        Target.TIME -> R.string.clock_time_color
        Target.TEMP -> R.string.clock_temp_color
        Target.DATE -> if (!settings.wxLine) R.string.clock_date_color else if (settings.date) R.string.clock_date_wxline_color else R.string.clock_wxline_color
    })

    private val rows = mutableListOf<Pair<Int, Target>>()

    private fun colorRow(id: Int, target: Target) {
        rows += id to target
        findViewById<View>(id).setOnClickListener { picking = target; picker.open(title(target), colorOf(target)) }
        paintColorRows()
    }

    private fun paintColorRows() {
        for ((id, target) in rows) {
            val row = findViewById<View>(id)
            val c = colorOf(target)
            row.findViewById<TextView>(R.id.color_title).text = title(target)
            row.findViewById<TextView>(R.id.color_value).text = ClockPalette.label(c)
            row.findViewById<ImageView>(R.id.color_swatch).setImageDrawable(SwatchDrawable(c, getColor(R.color.line_strong)))
            row.contentDescription = "${title(target)}: ${ClockPalette.label(c)}"
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (picker.isOpen) picker.close(keep = false) else super.onBackPressed()
    }

    /* ---------------- Preview and saving ---------------- */

    private fun drawPreview(s: ClockSettings = settings) {
        val preview = findViewById<ImageView>(R.id.preview)
        val (w, h) = ClockUpdater.sizeDp(this, widgetId)
        val r = ClockUpdater.render(this, s, w, h)
        // Real size when it fits; otherwise scaled down to the screen width.
        val container = findViewById<View>(R.id.preview_container)
        val maxW = container.width - container.paddingLeft - container.paddingRight
        val scale = if (maxW > 0) min(1f, maxW / (w * d)) else 1f
        preview.layoutParams = preview.layoutParams.apply { width = (w * d * scale).toInt(); height = (h * d * scale).toInt() }
        preview.setImageBitmap(r.bitmap)
    }

    private fun save() {
        store.saveClockSettings(widgetId, settings)
        ClockUpdater.update(this, widgetId)
        ClockTicker.schedule(this)
        // The weather may have just been turned on: fetch it if there's nothing saved yet
        if ((settings.weather || settings.wxLine) && store.forecastForCurrentPlace() == null) Refresh.now(this, intArrayOf(widgetId))
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
