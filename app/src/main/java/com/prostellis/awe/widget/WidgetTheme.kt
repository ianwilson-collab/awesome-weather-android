package com.prostellis.awe.widget

/** Text and panel colors for a widget, from the web app's light and dark themes. */
data class WidgetTheme(
    val dark: Boolean,
    val ink: Int,
    val ink2: Int,
    val ink3: Int,
    val accent: Int,
    val todayName: Int,
    val panel: Int,
    val today: Int,
    val rainBlock: Int,
    val amount: Int,
    val awe: Triple<Int, Int, Int>,
    val textShadow: Boolean,
    val icons: IconColors,
    /** Laid over a sky picture so text stays readable on busy scenes. */
    val veil: Int,
) {
    companion object {
        private val AWE_BRIGHT = Triple(0xFFFFC24A.toInt(), 0xFF95DABD.toInt(), 0xFF6CB4D5.toInt())
        private val AWE_DEEP = Triple(0xFFC98A06.toInt(), 0xFF2F9E78.toInt(), 0xFF2B7FB0.toInt())

        val LIGHT = WidgetTheme(
            dark = false,
            ink = 0xFF15202B.toInt(), ink2 = 0xFF4A5A6A.toInt(), ink3 = 0xFF75828F.toInt(),
            accent = 0xFF2F6FDB.toInt(), todayName = 0xFF2F6FDB.toInt(),
            panel = 0x9EFFFFFF.toInt(), today = 0x1A2F6FDB, rainBlock = 0xFF3F78C4.toInt(), amount = 0xFF3F78C4.toInt(),
            awe = AWE_DEEP, textShadow = false, veil = 0x38FFFFFF,
            icons = IconColors(
                stroke = 0xFF4A5A6A.toInt(), ink3 = 0xFF75828F.toInt(), sun = 0xFFF0A412.toInt(), cloudFill = 0xFFFFFFFF.toInt(),
                rain = 0xFF3F78C4.toInt(), moonInk = 0xFFFDF2C9.toInt(), moonIcon = 0xFFDFE2E7.toInt(), crater = 0xFFC3C8D0.toInt(),
                glowA = 0xFFFFFFFF.toInt(), glowB = 0xFFC3CFDD.toInt(), glowShadow = 0x29283C5A, lineInk = 0xFF3C4B5B.toInt(),
            ),
        )

        val DARK = WidgetTheme(
            dark = true,
            ink = 0xFFE8EEF4.toInt(), ink2 = 0xFFA8B5C2.toInt(), ink3 = 0xFF7C8996.toInt(),
            accent = 0xFF6EA1FF.toInt(), todayName = 0xFF6EA1FF.toInt(),
            panel = 0x17FFFFFF, today = 0x246EA1FF, rainBlock = 0xFF7FB0F0.toInt(), amount = 0xFF7FB0F0.toInt(),
            awe = AWE_BRIGHT, textShadow = false, veil = 0x2E000000,
            icons = IconColors(
                stroke = 0xFFA8B5C2.toInt(), ink3 = 0xFF7C8996.toInt(), sun = 0xFFFFC24A.toInt(), cloudFill = 0xFF2B3644.toInt(),
                rain = 0xFF7FB0F0.toInt(), moonInk = 0xFFF1E9C6.toInt(), moonIcon = 0xFFB8BCC3.toInt(), crater = 0xFF969BA4.toInt(),
                glowA = 0xFFEEF3F8.toInt(), glowB = 0xFF8797AA.toInt(), glowShadow = 0x47000000, lineInk = 0xFFC9D3DE.toInt(),
            ),
        )

        /** White text with a soft shadow, over a dark wallpaper (see-through widget) or a dark sky picture. */
        val ON_DARK = WidgetTheme(
            dark = true,
            ink = 0xFFFFFFFF.toInt(), ink2 = 0xFFE6EDF5.toInt(), ink3 = 0xFFCFD9E4.toInt(),
            accent = 0xFF6EA1FF.toInt(), todayName = 0xFFFFFFFF.toInt(),
            panel = 0x4D0C1622, today = 0x24FFFFFF, rainBlock = 0xFF7FB0F0.toInt(), amount = 0xFFD6E7FF.toInt(),
            awe = AWE_BRIGHT, textShadow = true, veil = 0x29000000,
            icons = IconColors(
                stroke = 0xFFE6EDF5.toInt(), ink3 = 0xFFCFD9E4.toInt(), sun = 0xFFFFC24A.toInt(), cloudFill = 0xFFF2F5F9.toInt(),
                rain = 0xFF9CC4F5.toInt(), moonInk = 0xFFF1E9C6.toInt(), moonIcon = 0xFFDFE2E7.toInt(), crater = 0xFFC3C8D0.toInt(),
                glowA = 0xFFFFFFFF.toInt(), glowB = 0xFFB6C3D3.toInt(), glowShadow = 0x47000000, lineInk = 0xFFE6EDF5.toInt(),
            ),
        )

        /** Once the background is this see-through, text follows the wallpaper instead of the sky. */
        const val WALLPAPER_TEXT_FROM = 40

        /**
         * Text colors that read on what's behind them: the wallpaper once the widget is see-through enough,
         * otherwise the sky picture when the style has one ([pictureIsDark]), otherwise the phone's light or dark theme.
         */
        fun pick(phoneDark: Boolean, transparency: Int, wallpaperSupportsDarkText: Boolean, pictureIsDark: Boolean? = null): WidgetTheme = when {
            transparency >= WALLPAPER_TEXT_FROM -> if (wallpaperSupportsDarkText) LIGHT else ON_DARK
            pictureIsDark == null -> if (phoneDark) DARK else LIGHT
            pictureIsDark -> ON_DARK
            else -> LIGHT
        }
    }
}

/** Helpers for temperature colors: Auto is the widget's own text color, or on the 1×1 white or near-black, whichever reads better. */
object TempColors {
    /** White or near-black, whichever stands out more against [behind] (WCAG contrast). */
    fun readableOn(behind: Int): Int =
        if (contrast(0xFFFFFFFF.toInt(), behind) >= contrast(NEAR_BLACK, behind)) 0xFFFFFFFF.toInt() else NEAR_BLACK

    /** True for light colors, which get a soft shadow so they hold up on light backgrounds. */
    fun isLight(c: Int) = luminance(c) > .5

    private const val NEAR_BLACK = 0xFF141A22.toInt()

    fun luminance(c: Int): Double {
        fun ch(v: Int): Double { val s = v / 255.0; return if (s <= .03928) s / 12.92 else Math.pow((s + .055) / 1.055, 2.4) }
        return .2126 * ch((c shr 16) and 0xFF) + .7152 * ch((c shr 8) and 0xFF) + .0722 * ch(c and 0xFF)
    }

    private fun contrast(a: Int, b: Int): Double {
        val x = luminance(a); val y = luminance(b)
        return (maxOf(x, y) + .05) / (minOf(x, y) + .05)
    }
}

/** Glow strength: the halo behind a widget's temperatures, set per widget. Medium is the default. */
object Glow {
    val NAMES = listOf("Off", "Soft", "Medium", "Strong", "Max")
    const val DEFAULT = 2

    /** Halo colors: near-black behind light text, white behind dark text. */
    const val DARK = 0xFF0A1018.toInt()
    const val LIGHT = 0xFFFFFFFF.toInt()

    // Blur radius (dp at 20dp text) and opacity of each pass; a tight pass keeps edges crisp, wider ones soften into the sky
    private val LAYERS = listOf(
        emptyList(),
        listOf(2f to .45f),
        listOf(1.2f to .6f, 4f to .45f),
        listOf(1.2f to .75f, 3f to .6f, 7f to .45f),
        listOf(1f to .9f, 2.5f to .75f, 5f to .6f, 10f to .45f),
    )

    fun layers(step: Int): List<Pair<Float, Float>> = LAYERS[step.coerceIn(0, LAYERS.size - 1)]
}
