package com.prostellis.awe.widget

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.Intent
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import android.os.Bundle
import android.text.InputType
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.GridLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.Switch
import android.widget.TextView
import com.prostellis.awe.R
import com.prostellis.awe.data.Store
import kotlin.math.min

/**
 * AWE Clock settings: a live preview on the real wallpaper, then the time format and style, the three colors
 * (each through the same picker), glow, transparency, weather and the day and date. Opens when the clock is
 * placed and by long-pressing it.
 */
class ClockSettingsActivity : Activity() {

    private var widgetId = AppWidgetManager.INVALID_APPWIDGET_ID
    private lateinit var store: Store
    private var settings = ClockSettings()
    private val d by lazy { resources.displayMetrics.density }

    /** The color picker while it's open: which color, the tab, the square's hue/saturation/brightness, and the choices. */
    private class Pick(val target: Target, var tab: ColorFamily, var h: Float, var s: Float, var v: Float, val before: ClockColor, var choice: ClockColor)
    private enum class Target(val title: Int) { TIME(R.string.clock_time_color), TEMP(R.string.clock_temp_color), DATE(R.string.clock_date_color) }
    private var pick: Pick? = null

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
        colorRow(R.id.row_time_color, Target.TIME)
        colorRow(R.id.row_temp_color, Target.TEMP)
        colorRow(R.id.row_date_color, Target.DATE)
        switch(R.id.weather, settings.weather) { settings = settings.copy(weather = it) }
        switch(R.id.hilo, settings.hiLo) { settings = settings.copy(hiLo = it) }
        switch(R.id.date, settings.date) { settings = settings.copy(date = it) }
        findViewById<RadioGroup>(R.id.date_pos_group).apply {
            check(if (settings.datePos == DatePos.ABOVE) R.id.date_above else R.id.date_below)
            setOnCheckedChangeListener { _, id -> settings = settings.copy(datePos = if (id == R.id.date_above) DatePos.ABOVE else DatePos.BELOW); changed() }
        }
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
        drawPreview()
    }

    /** High and low and the temperature color show only with the weather on; position and date color only with the date on. */
    private fun showDependentRows() {
        fun show(id: Int, on: Boolean) { findViewById<View>(id).visibility = if (on) View.VISIBLE else View.GONE }
        show(R.id.hilo_row, settings.weather)
        show(R.id.row_temp_color, settings.weather)
        show(R.id.date_pos_row, settings.date)
        show(R.id.row_date_color, settings.date)
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

    private fun rowId(target: Target) = when (target) { Target.TIME -> R.id.row_time_color; Target.TEMP -> R.id.row_temp_color; Target.DATE -> R.id.row_date_color }

    private fun colorRow(id: Int, target: Target) {
        val row = findViewById<View>(id)
        row.findViewById<TextView>(R.id.color_title).setText(target.title)
        row.setOnClickListener { openPicker(target) }
        paintColorRow(target)
    }

    private fun paintColorRow(target: Target) {
        val row = findViewById<View>(rowId(target))
        val c = colorOf(target)
        row.findViewById<TextView>(R.id.color_value).text = ClockPalette.label(c)
        row.findViewById<ImageView>(R.id.color_swatch).setImageDrawable(SwatchDrawable(c, getColor(R.color.line_strong)))
        row.contentDescription = "${getString(target.title)}: ${ClockPalette.label(c)}"
    }

    private fun openPicker(target: Target) {
        val cur = colorOf(target)
        val start = when (cur.family) {
            ColorFamily.CUSTOM -> cur.hex
            ColorFamily.SOLID -> ClockPalette.SOLID[cur.index].second ?: 0xFFFFC200.toInt()
            else -> 0xFFFFC200.toInt()
        }
        val hsv = FloatArray(3).also { Color.colorToHSV(start, it) }
        val tab = if (cur.family == ColorFamily.METAL || cur.family == ColorFamily.GRAD) cur.family else ColorFamily.CUSTOM
        pick = Pick(target, tab, hsv[0], hsv[1], hsv[2], cur, cur)
        findViewById<View>(R.id.body).visibility = View.GONE
        findViewById<ScrollView>(R.id.picker).apply { visibility = View.VISIBLE; scrollTo(0, 0) }
        paintPicker()
    }

    private fun closePicker(keep: Boolean) {
        val p = pick ?: return
        settings = withColor(p.target, if (keep) p.choice else p.before)
        pick = null
        findViewById<View>(R.id.picker).visibility = View.GONE
        findViewById<View>(R.id.body).visibility = View.VISIBLE
        paintColorRow(p.target)
        drawPreview()
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (pick != null) closePicker(keep = false) else super.onBackPressed()
    }

    /** Builds the picker for the current tab: the square, hue strip and 16 quick colors, or the 12 metallics or gradients. */
    private fun paintPicker() {
        val p = pick ?: return
        val body = findViewById<LinearLayout>(R.id.picker_body)
        body.removeAllViews()
        body.addView(TextView(this).apply { setTextAppearance(R.style.Awe_CardTitle); setText(p.target.title) })

        val tabs = RadioGroup(this).apply { orientation = RadioGroup.HORIZONTAL }
        for ((family, label) in listOf(ColorFamily.CUSTOM to R.string.picker_custom, ColorFamily.METAL to R.string.picker_metal, ColorFamily.GRAD to R.string.picker_grad)) {
            tabs.addView(pill(label).apply { id = View.generateViewId(); isChecked = p.tab == family; tag = family })
        }
        tabs.setOnCheckedChangeListener { group, id -> p.tab = group.findViewById<View>(id).tag as ColorFamily; paintPicker() }
        body.addView(tabs, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = (10 * d).toInt(); marginStart = (-6 * d).toInt() })

        val after = ImageView(this)
        val hex = EditText(this)
        if (p.tab == ColorFamily.CUSTOM) {
            val sv = SvSquareView(this).apply { hue = p.h; set(p.s, p.v); contentDescription = getString(R.string.picker_sv) }
            val strip = HueStripView(this).apply { hue = p.h; contentDescription = getString(R.string.picker_hue) }
            fun choose() {
                p.choice = ClockColor(ColorFamily.CUSTOM, hex = Color.HSVToColor(floatArrayOf(p.h, p.s, p.v)))
                after.setImageDrawable(SwatchDrawable(p.choice, getColor(R.color.line_strong)))
                hex.setText(hexOf(p.choice.hex))
                preview()
            }
            sv.onChange = { s, v -> p.s = s; p.v = v; choose() }
            strip.onChange = { h -> p.h = h; sv.hue = h; if (p.s == 0f && p.v == 0f) { p.s = 1f; p.v = 1f; sv.set(1f, 1f) }; choose() }
            body.addView(LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                addView(sv, LinearLayout.LayoutParams(0, (170 * d).toInt(), 1f))
                addView(strip, LinearLayout.LayoutParams((28 * d).toInt(), (170 * d).toInt()).apply { marginStart = (12 * d).toInt() })
            }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = (12 * d).toInt() })
            // The 16 solid presets as quick picks, Auto first
            val quick = GridLayout(this).apply { columnCount = 8 }
            ClockPalette.SOLID.forEachIndexed { i, (name, _) ->
                quick.addView(ImageView(this).apply {
                    val c = ClockColor(ColorFamily.SOLID, i)
                    setImageDrawable(SwatchDrawable(c, getColor(R.color.line_strong)))
                    contentDescription = name
                    setPadding((4 * d).toInt(), (4 * d).toInt(), (4 * d).toInt(), (4 * d).toInt())
                    setOnClickListener {
                        p.choice = c
                        ClockPalette.SOLID[i].second?.let { col -> FloatArray(3).also { Color.colorToHSV(col, it) }.let { hsv -> p.h = hsv[0]; p.s = hsv[1]; p.v = hsv[2] } }
                        paintPicker()
                    }
                }, GridLayout.LayoutParams(GridLayout.spec(GridLayout.UNDEFINED), GridLayout.spec(GridLayout.UNDEFINED, 1f)).apply { width = 0; height = (40 * d).toInt() })
            }
            body.addView(quick, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = (10 * d).toInt() })
        } else {
            val list = if (p.tab == ColorFamily.METAL) ClockPalette.METAL else ClockPalette.GRAD
            val grid = GridLayout(this).apply { columnCount = 3 }
            list.forEachIndexed { i, (name, _) ->
                val c = ClockColor(p.tab, i)
                grid.addView(LinearLayout(this).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    background = getDrawable(R.drawable.face_button)
                    isSelected = p.choice == c
                    contentDescription = name
                    setPadding((8 * d).toInt(), (8 * d).toInt(), (6 * d).toInt(), (8 * d).toInt())
                    addView(ImageView(context).apply { setImageDrawable(SwatchDrawable(c, getColor(R.color.line_strong), round = false)) },
                        LinearLayout.LayoutParams((26 * d).toInt(), (26 * d).toInt()))
                    addView(TextView(context).apply {
                        text = name; setTextColor(getColor(R.color.ink)); setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f); maxLines = 2
                        typeface = resources.getFont(R.font.dm_sans)
                        importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
                    }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { marginStart = (6 * d).toInt() })
                    setOnClickListener { p.choice = c; paintPicker() }
                }, GridLayout.LayoutParams(GridLayout.spec(GridLayout.UNDEFINED), GridLayout.spec(GridLayout.UNDEFINED, 1f)).apply {
                    width = 0; setMargins((3 * d).toInt(), (3 * d).toInt(), (3 * d).toInt(), (3 * d).toInt())
                })
            }
            body.addView(grid, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = (12 * d).toInt() })
        }

        // Before → after, and the hex code on the Custom tab
        val outline = getColor(R.color.line_strong)
        val ba = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        val sw = (30 * d).toInt()
        ba.addView(ImageView(this).apply { setImageDrawable(SwatchDrawable(p.before, outline)); importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO }, LinearLayout.LayoutParams(sw, sw))
        ba.addView(TextView(this).apply { text = "→"; setTextColor(getColor(R.color.ink3)); setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f); importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO },
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { marginStart = (8 * d).toInt(); marginEnd = (8 * d).toInt() })
        after.setImageDrawable(SwatchDrawable(p.choice, outline))
        after.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        ba.addView(after, LinearLayout.LayoutParams(sw, sw))
        if (p.tab == ColorFamily.CUSTOM) {
            hex.apply {
                setText(when (p.choice.family) {
                    ColorFamily.CUSTOM -> hexOf(p.choice.hex)
                    else -> ClockPalette.stops(p.choice)?.takeIf { it.size == 1 }?.let { hexOf(it[0]) } ?: ""
                })
                contentDescription = getString(R.string.picker_hex)
                hint = "#FFC200"
                inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS or InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS
                imeOptions = EditorInfo.IME_ACTION_DONE
                setSingleLine()
                setTextColor(getColor(R.color.ink))
                typeface = resources.getFont(R.font.dm_sans)
                setOnEditorActionListener { _, _, _ -> applyHex(text.toString()); true }
            }
            ba.addView(hex, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { marginStart = (14 * d).toInt() })
        }
        body.addView(ba, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = (12 * d).toInt() })

        val actions = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.END }
        actions.addView(pill(R.string.picker_cancel).apply { isChecked = false; setOnClickListener { closePicker(keep = false) } })
        actions.addView(pill(R.string.picker_ok).apply { isChecked = true; setOnClickListener { closePicker(keep = true) } })
        body.addView(actions, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = (14 * d).toInt() })
        preview()
    }

    /** A typed hex code: applied when it's a full six-digit color, otherwise put back to the current color. */
    private fun applyHex(typed: String) {
        val p = pick ?: return
        val v = typed.trim().removePrefix("#")
        val c = if (Regex("[0-9a-fA-F]{6}").matches(v)) (0xFF000000 or v.toLong(16)).toInt() else null
        if (c == null) { paintPicker(); return }
        val hsv = FloatArray(3).also { Color.colorToHSV(c, it) }
        p.h = hsv[0]; p.s = hsv[1]; p.v = hsv[2]
        p.choice = ClockColor(ColorFamily.CUSTOM, hex = c)
        paintPicker()
    }

    private fun hexOf(c: Int) = String.format(java.util.Locale.US, "#%06X", c and 0xFFFFFF)

    /** A pill-shaped choice, like the format and position buttons. */
    private fun pill(text: Int) = RadioButton(this).apply {
        setText(text)
        buttonDrawable = null
        background = getDrawable(R.drawable.pill)
        setTextColor(getColorStateList(R.color.pill_text))
        typeface = resources.getFont(R.font.dm_sans)
        fontVariationSettings = "'wght' 600"
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
        gravity = Gravity.CENTER
        minWidth = (64 * d).toInt()
        setPadding((14 * d).toInt(), 0, (14 * d).toInt(), 0)
        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, (36 * d).toInt()).apply { marginStart = (6 * d).toInt() }
    }

    /* ---------------- Preview and saving ---------------- */

    /** The preview with the picker's choice applied (not saved until OK and Done). */
    private fun preview() {
        val p = pick
        drawPreview(if (p == null) settings else withColor(p.target, p.choice))
    }

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
        if (settings.weather && store.forecastForCurrentPlace() == null) Refresh.now(this, intArrayOf(widgetId))
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
