package com.prostellis.awe.widget

import android.content.Context
import android.graphics.LinearGradient
import android.graphics.Shader
import android.graphics.Typeface
import java.util.Locale
import com.prostellis.awe.R

/** One of the clock's ten digital faces: a font, the weight it's drawn at, and its letter spacing (em). */
data class ClockFace(val id: String, val name: String, val font: Int, val weight: Int, val track: Float, val ghost: Boolean = false) {
    fun typeface(context: Context): Typeface = context.resources.getFont(font)

    /** DM Sans is a variable font; the others are single-weight files, so they ignore this. */
    val variation: String? get() = if (font == R.font.dm_sans) "'wght' $weight" else null

    companion object {
        val ALL = listOf(
            ClockFace("clean", "Clean", R.font.dm_sans, 600, -.03f),
            ClockFace("rounded", "Rounded", R.font.clock_nunito, 800, -.02f),
            ClockFace("hairline", "Hairline", R.font.clock_outfit, 200, -.01f),
            // LCD shows faint unlit digits behind the time
            ClockFace("lcd", "LCD", R.font.clock_share_tech_mono, 400, 0f, ghost = true),
            ClockFace("condensed", "Condensed", R.font.clock_oswald, 500, 0f),
            ClockFace("heavy", "Heavy", R.font.clock_archivo_black, 400, -.02f),
            ClockFace("tech", "Tech", R.font.clock_orbitron, 600, 0f),
            ClockFace("softmono", "Soft mono", R.font.clock_spline_mono, 500, -.02f),
            ClockFace("tall", "Tall", R.font.clock_teko, 300, 0f),
            ClockFace("geo", "Geometric", R.font.clock_lexend, 700, -.03f),
        )

        fun byId(id: String?): ClockFace = ALL.firstOrNull { it.id == id } ?: ALL[0]
    }
}

/** The kinds of color the picker offers. Solid is the 16 presets (Auto first); Custom is any color from the square. */
enum class ColorFamily { SOLID, CUSTOM, METAL, GRAD }

/** A color for the time, the date or the temperature: a preset ([index]) in its family, or a custom [hex] color. */
data class ClockColor(val family: ColorFamily, val index: Int = 0, val hex: Int = 0) {
    val isAuto get() = family == ColorFamily.SOLID && index == 0

    fun encode(): String = when (family) {
        ColorFamily.CUSTOM -> "custom:${String.format(Locale.US, "%06x", hex and 0xFFFFFF)}"
        else -> "${family.name.lowercase()}:$index"
    }

    companion object {
        val AUTO = ClockColor(ColorFamily.SOLID, 0)

        fun decode(s: String?): ClockColor {
            val parts = s?.split(":") ?: return AUTO
            if (parts.size != 2) return AUTO
            val family = ColorFamily.entries.firstOrNull { it.name.lowercase() == parts[0] } ?: return AUTO
            return if (family == ColorFamily.CUSTOM) {
                parts[1].toLongOrNull(16)?.let { ClockColor(family, hex = (0xFF000000 or it).toInt()) } ?: AUTO
            } else {
                val i = parts[1].toIntOrNull() ?: return AUTO
                if (i in ClockPalette.list(family).indices) ClockColor(family, i) else AUTO
            }
        }
    }
}

/** A text fill: a plain color, or a shader for metallic and gradient colors. [light] picks the glow's halo (dark behind light). */
class TextFill(val color: Int, val shader: Shader?, val light: Boolean)

/** The picker's presets: 16 solids (the widgets' temperature colors plus five more), 12 metallics and 12 gradients. */
object ClockPalette {
    val SOLID: List<Pair<String, Int?>> = listOf(
        "Auto" to null, "White" to 0xFFFFFFFF.toInt(), "Soft gray" to 0xFFD5DBE2.toInt(), "Charcoal" to 0xFF3A4552.toInt(),
        "Black" to 0xFF0F141A.toInt(), "Sunshine" to 0xFFFFD23F.toInt(), "Orange" to 0xFFFF9A3C.toInt(), "Coral" to 0xFFFF6B6B.toInt(),
        "Mint" to 0xFF5FE0B4.toInt(), "Sky blue" to 0xFF5AB8FF.toInt(), "Lavender" to 0xFFB9A3FF.toInt(), "Cream" to 0xFFFFF1CC.toInt(),
        "Rose" to 0xFFFF8FB8.toInt(), "Lime" to 0xFFB8E35A.toInt(), "Teal" to 0xFF2FC4C4.toInt(), "Indigo" to 0xFF6F78FF.toInt(),
    )

    /** Dark → mid → bright highlight band → mid → dark, top to bottom. */
    val METAL: List<Pair<String, IntArray>> = listOf(
        "Gold" to rgb(0x6b4a0e, 0xc9921c, 0xfff1a8, 0xd9a52b, 0x7a5410),
        "Silver" to rgb(0x5f6a76, 0xb9c3cd, 0xffffff, 0xc3ccd5, 0x66717d),
        "Copper" to rgb(0x5a2a12, 0xb8693a, 0xffd2b0, 0xc47a48, 0x62301a),
        "Gunmetal" to rgb(0x0d1117, 0x3b434d, 0x9aa6b2, 0x48515c, 0x11161c),
        "Blue" to rgb(0x0f2a5c, 0x2f6fdb, 0xbfe0ff, 0x3d7fe6, 0x123066),
        "Pink" to rgb(0x6e1f48, 0xe06aa6, 0xffe0f0, 0xe979b2, 0x78244f),
        "Purple" to rgb(0x33185e, 0x8a5cdf, 0xe6dbff, 0x9a6ee8, 0x3a1c69),
        "Red" to rgb(0x5c0d12, 0xcc2e36, 0xffc9c4, 0xd73e44, 0x661016),
        "Rose gold" to rgb(0x6e3a2e, 0xc98a7a, 0xffe6dc, 0xd99c8b, 0x7a4234),
        "Platinum" to rgb(0x7d8794, 0xe4e8ee, 0xffffff, 0xd3d9e1, 0x8a94a1),
        "Emerald" to rgb(0x0b3d2c, 0x1f9a6e, 0xc8ffe8, 0x2fae80, 0x0e4734),
        "Bronze" to rgb(0x3f2710, 0x8f6230, 0xf3d6a6, 0xa2733b, 0x4a2f15),
    )
    private val METAL_STOPS = floatArrayOf(0f, .4f, .52f, .62f, 1f)

    /** Top to bottom, evenly spaced; built from the app's temperature-bar colors. */
    val GRAD: List<Pair<String, IntArray>> = listOf(
        "Heat" to rgb(0xe35a3a, 0xf0913d, 0xe7c14a), "Ember" to rgb(0xb3202c, 0xe35a3a, 0xf0913d),
        "Sunrise" to rgb(0xf0913d, 0xe7c14a), "Meadow" to rgb(0xe7c14a, 0x47b8a0),
        "Ocean" to rgb(0x4a9ede, 0x5b6ee1), "Aurora green" to rgb(0x5fe0b4, 0x47b8a0, 0x4a9ede),
        "Aurora pink" to rgb(0xff8fc8, 0xc58cff, 0x7a5cff), "Spectrum" to rgb(0xe35a3a, 0xf0913d, 0xe7c14a, 0x47b8a0, 0x4a9ede, 0x5b6ee1),
        "Twilight" to rgb(0x8a5cdf, 0x5b6ee1, 0x4a9ede), "Candy" to rgb(0xff8fc8, 0xff6b6b, 0xf0913d),
        "Mint aqua" to rgb(0x5fe0b4, 0x47b8a0, 0x4ad0e6), "Sunset" to rgb(0xff6b6b, 0xc58cff, 0x5b6ee1),
    )

    private fun rgb(vararg c: Int) = IntArray(c.size) { 0xFF000000.toInt() or c[it] }

    fun list(family: ColorFamily): List<Any> = when (family) {
        ColorFamily.SOLID -> SOLID
        ColorFamily.METAL -> METAL
        ColorFamily.GRAD -> GRAD
        ColorFamily.CUSTOM -> emptyList()
    }

    fun familyName(family: ColorFamily) = when (family) {
        ColorFamily.SOLID -> "Solid"; ColorFamily.CUSTOM -> "Custom"; ColorFamily.METAL -> "Metallic"; ColorFamily.GRAD -> "Gradient"
    }

    /** "Metallic · Gold", "Custom · #FFC200", "Solid · Auto". */
    fun label(c: ClockColor): String = when (c.family) {
        ColorFamily.CUSTOM -> "Custom · #${String.format(Locale.US, "%06X", c.hex and 0xFFFFFF)}"
        ColorFamily.SOLID -> "Solid · ${SOLID[c.index].first}"
        ColorFamily.METAL -> "Metallic · ${METAL[c.index].first}"
        ColorFamily.GRAD -> "Gradient · ${GRAD[c.index].first}"
    }

    /** The colors a swatch shows: one for a solid or custom color (null for Auto), the stops for metallic and gradient. */
    fun stops(c: ClockColor): IntArray? = when (c.family) {
        ColorFamily.SOLID -> SOLID[c.index].second?.let { intArrayOf(it) }
        ColorFamily.CUSTOM -> intArrayOf(c.hex)
        ColorFamily.METAL -> METAL[c.index].second
        ColorFamily.GRAD -> GRAD[c.index].second
    }

    /** Stop positions for [stops] (null = evenly spaced). */
    fun positions(c: ClockColor): FloatArray? = if (c.family == ColorFamily.METAL) METAL_STOPS else null

    /** The fill for text whose letters run from [top] to [bottom]; Auto uses [auto], the widget's own text color. */
    fun fill(c: ClockColor, auto: Int, top: Float, bottom: Float): TextFill {
        val stops = stops(c)
        if (stops == null || stops.size == 1) {
            val color = stops?.get(0) ?: auto
            return TextFill(color, null, TempColors.isLight(color))
        }
        val shader = LinearGradient(0f, top, 0f, bottom, stops, positions(c), Shader.TileMode.CLAMP)
        return TextFill(0xFFFFFFFF.toInt(), shader, stops.map(TempColors::luminance).average() > .35)
    }
}

enum class TimeFormat { PHONE, H12, H24 }
enum class DatePos { ABOVE, BELOW }

/** Settings for one clock widget. Every color starts on Auto. */
data class ClockSettings(
    val face: String = "clean",
    val format: TimeFormat = TimeFormat.PHONE,
    val timeColor: ClockColor = ClockColor.AUTO,
    val glow: Int = Glow.DEFAULT,
    val transparency: Int = 0,
    val weather: Boolean = true,
    val hiLo: Boolean = false,
    val tempColor: ClockColor = ClockColor.AUTO,
    val date: Boolean = false,
    val datePos: DatePos = DatePos.BELOW,
    val dateColor: ClockColor = ClockColor.AUTO,
)
