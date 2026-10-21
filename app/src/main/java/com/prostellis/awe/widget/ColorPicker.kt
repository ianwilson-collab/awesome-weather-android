package com.prostellis.awe.widget

import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import android.graphics.Color
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
import android.widget.TextView
import com.prostellis.awe.R

/**
 * The color picker the Clock and weather widget settings share: Custom (a saturation-and-brightness square, a hue
 * strip, the 16 solid presets and a hex code), Metallic and Gradient. It opens over the settings ([settings] hides,
 * [scroll] shows with the picker built into [body]), so the live preview above stays in view. [onPreview] gets each
 * choice as it's made; [onClose] gets the color to keep (the one it opened with, on Cancel). Back works like Cancel.
 */
class ColorPicker(
    private val activity: ComponentActivity,
    private val settings: View,
    private val scroll: ScrollView,
    private val body: LinearLayout,
    private val onPreview: (ClockColor) -> Unit,
    private val onClose: (ClockColor) -> Unit,
) {
    private val d = activity.resources.displayMetrics.density

    /** The picker while it's open: its title, the tab, the square's hue/saturation/brightness, and the choices. */
    private class Pick(val title: String, var tab: ColorFamily, var h: Float, var s: Float, var v: Float, val before: ClockColor, var choice: ClockColor)
    private var pick: Pick? = null

    /** While the picker is open, Back cancels it instead of leaving the settings. */
    private val back = object : OnBackPressedCallback(false) {
        override fun handleOnBackPressed() = close(keep = false)
    }

    init {
        activity.onBackPressedDispatcher.addCallback(activity, back)
    }

    fun open(title: String, current: ClockColor) {
        val start = when (current.family) {
            ColorFamily.CUSTOM -> current.hex
            ColorFamily.SOLID -> ClockPalette.SOLID[current.index].second ?: 0xFFFFC200.toInt()
            else -> 0xFFFFC200.toInt()
        }
        val hsv = FloatArray(3).also { Color.colorToHSV(start, it) }
        val tab = if (current.family == ColorFamily.METAL || current.family == ColorFamily.GRAD) current.family else ColorFamily.CUSTOM
        pick = Pick(title, tab, hsv[0], hsv[1], hsv[2], current, current)
        back.isEnabled = true
        settings.visibility = View.GONE
        scroll.apply { visibility = View.VISIBLE; scrollTo(0, 0) }
        paint()
    }

    fun close(keep: Boolean) {
        val p = pick ?: return
        pick = null
        back.isEnabled = false
        scroll.visibility = View.GONE
        settings.visibility = View.VISIBLE
        onClose(if (keep) p.choice else p.before)
    }

    /** Builds the picker for the current tab: the square, hue strip and 16 quick colors, or the 12 metallics or gradients. */
    private fun paint() {
        val p = pick ?: return
        body.removeAllViews()
        body.addView(TextView(activity).apply { setTextAppearance(R.style.Awe_CardTitle); text = p.title })

        val tabs = RadioGroup(activity).apply { orientation = RadioGroup.HORIZONTAL }
        for ((family, label) in listOf(ColorFamily.CUSTOM to R.string.picker_custom, ColorFamily.METAL to R.string.picker_metal, ColorFamily.GRAD to R.string.picker_grad)) {
            tabs.addView(pill(label).apply { id = View.generateViewId(); isChecked = p.tab == family; tag = family })
        }
        tabs.setOnCheckedChangeListener { group, id -> p.tab = group.findViewById<View>(id).tag as ColorFamily; paint() }
        body.addView(tabs, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = (10 * d).toInt(); marginStart = (-6 * d).toInt() })

        val after = ImageView(activity)
        val hex = EditText(activity)
        val outline = activity.getColor(R.color.line_strong)
        if (p.tab == ColorFamily.CUSTOM) {
            val sv = SvSquareView(activity).apply { hue = p.h; set(p.s, p.v); contentDescription = activity.getString(R.string.picker_sv) }
            val strip = HueStripView(activity).apply { hue = p.h; contentDescription = activity.getString(R.string.picker_hue) }
            fun choose() {
                p.choice = ClockColor(ColorFamily.CUSTOM, hex = Color.HSVToColor(floatArrayOf(p.h, p.s, p.v)))
                after.setImageDrawable(SwatchDrawable(p.choice, outline))
                hex.setText(hexOf(p.choice.hex))
                onPreview(p.choice)
            }
            sv.onChange = { s, v -> p.s = s; p.v = v; choose() }
            strip.onChange = { h -> p.h = h; sv.hue = h; if (p.s == 0f && p.v == 0f) { p.s = 1f; p.v = 1f; sv.set(1f, 1f) }; choose() }
            body.addView(LinearLayout(activity).apply {
                orientation = LinearLayout.HORIZONTAL
                addView(sv, LinearLayout.LayoutParams(0, (170 * d).toInt(), 1f))
                addView(strip, LinearLayout.LayoutParams((28 * d).toInt(), (170 * d).toInt()).apply { marginStart = (12 * d).toInt() })
            }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = (12 * d).toInt() })
            // The 16 solid presets as quick picks, Auto first
            val quick = GridLayout(activity).apply { columnCount = 8 }
            ClockPalette.SOLID.forEachIndexed { i, (name, _) ->
                quick.addView(ImageView(activity).apply {
                    val c = ClockColor(ColorFamily.SOLID, i)
                    setImageDrawable(SwatchDrawable(c, outline))
                    contentDescription = name
                    setPadding((4 * d).toInt(), (4 * d).toInt(), (4 * d).toInt(), (4 * d).toInt())
                    setOnClickListener {
                        p.choice = c
                        ClockPalette.SOLID[i].second?.let { col -> FloatArray(3).also { Color.colorToHSV(col, it) }.let { hsv -> p.h = hsv[0]; p.s = hsv[1]; p.v = hsv[2] } }
                        paint()
                    }
                }, GridLayout.LayoutParams(GridLayout.spec(GridLayout.UNDEFINED), GridLayout.spec(GridLayout.UNDEFINED, 1f)).apply { width = 0; height = (40 * d).toInt() })
            }
            body.addView(quick, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = (10 * d).toInt() })
        } else {
            val list = if (p.tab == ColorFamily.METAL) ClockPalette.METAL else ClockPalette.GRAD
            val grid = GridLayout(activity).apply { columnCount = 3 }
            list.forEachIndexed { i, (name, _) ->
                val c = ClockColor(p.tab, i)
                grid.addView(LinearLayout(activity).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    background = activity.getDrawable(R.drawable.face_button)
                    isSelected = p.choice == c
                    contentDescription = name
                    setPadding((8 * d).toInt(), (8 * d).toInt(), (6 * d).toInt(), (8 * d).toInt())
                    addView(ImageView(context).apply { setImageDrawable(SwatchDrawable(c, outline, round = false)) },
                        LinearLayout.LayoutParams((26 * d).toInt(), (26 * d).toInt()))
                    addView(TextView(context).apply {
                        text = name; setTextColor(activity.getColor(R.color.ink)); setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f); maxLines = 2
                        typeface = resources.getFont(R.font.dm_sans)
                        importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
                    }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { marginStart = (6 * d).toInt() })
                    setOnClickListener { p.choice = c; paint() }
                }, GridLayout.LayoutParams(GridLayout.spec(GridLayout.UNDEFINED), GridLayout.spec(GridLayout.UNDEFINED, 1f)).apply {
                    width = 0; setMargins((3 * d).toInt(), (3 * d).toInt(), (3 * d).toInt(), (3 * d).toInt())
                })
            }
            body.addView(grid, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = (12 * d).toInt() })
        }

        // Before → after, and the hex code on the Custom tab
        val ba = LinearLayout(activity).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        val sw = (30 * d).toInt()
        ba.addView(ImageView(activity).apply { setImageDrawable(SwatchDrawable(p.before, outline)); importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO }, LinearLayout.LayoutParams(sw, sw))
        ba.addView(TextView(activity).apply { text = "→"; setTextColor(activity.getColor(R.color.ink3)); setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f); importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO },
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
                contentDescription = activity.getString(R.string.picker_hex)
                hint = "#FFC200"
                inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS or InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS
                imeOptions = EditorInfo.IME_ACTION_DONE
                setSingleLine()
                setTextColor(activity.getColor(R.color.ink))
                typeface = resources.getFont(R.font.dm_sans)
                setOnEditorActionListener { _, _, _ -> applyHex(text.toString()); true }
            }
            ba.addView(hex, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { marginStart = (14 * d).toInt() })
        }
        body.addView(ba, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = (12 * d).toInt() })

        val actions = LinearLayout(activity).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.END }
        actions.addView(pill(R.string.picker_cancel).apply { isChecked = false; setOnClickListener { close(keep = false) } })
        actions.addView(pill(R.string.picker_ok).apply { isChecked = true; setOnClickListener { close(keep = true) } })
        body.addView(actions, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = (14 * d).toInt() })
        onPreview(p.choice)
    }

    /** A typed hex code: applied when it's a full six-digit color, otherwise put back to the current color. */
    private fun applyHex(typed: String) {
        val p = pick ?: return
        val v = typed.trim().removePrefix("#")
        val c = if (Regex("[0-9a-fA-F]{6}").matches(v)) (0xFF000000 or v.toLong(16)).toInt() else null
        if (c == null) { paint(); return }
        val hsv = FloatArray(3).also { Color.colorToHSV(c, it) }
        p.h = hsv[0]; p.s = hsv[1]; p.v = hsv[2]
        p.choice = ClockColor(ColorFamily.CUSTOM, hex = c)
        paint()
    }

    private fun hexOf(c: Int) = String.format(java.util.Locale.US, "#%06X", c and 0xFFFFFF)

    /** A pill-shaped choice, like the format and position buttons. */
    private fun pill(text: Int) = RadioButton(activity).apply {
        setText(text)
        buttonDrawable = null
        background = activity.getDrawable(R.drawable.pill)
        setTextColor(activity.getColorStateList(R.color.pill_text))
        typeface = resources.getFont(R.font.dm_sans)
        fontVariationSettings = "'wght' 600"
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
        gravity = Gravity.CENTER
        minWidth = (64 * d).toInt()
        setPadding((14 * d).toInt(), 0, (14 * d).toInt(), 0)
        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, (36 * d).toInt()).apply { marginStart = (6 * d).toInt() }
    }
}
