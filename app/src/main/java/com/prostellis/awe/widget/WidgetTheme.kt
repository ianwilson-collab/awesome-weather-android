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
) {
    companion object {
        private val AWE_BRIGHT = Triple(0xFFFFC24A.toInt(), 0xFF95DABD.toInt(), 0xFF6CB4D5.toInt())
        private val AWE_DEEP = Triple(0xFFC98A06.toInt(), 0xFF2F9E78.toInt(), 0xFF2B7FB0.toInt())

        val LIGHT = WidgetTheme(
            dark = false,
            ink = 0xFF15202B.toInt(), ink2 = 0xFF4A5A6A.toInt(), ink3 = 0xFF75828F.toInt(),
            accent = 0xFF2F6FDB.toInt(), todayName = 0xFF2F6FDB.toInt(),
            panel = 0x9EFFFFFF.toInt(), today = 0x1A2F6FDB, rainBlock = 0xFF3F78C4.toInt(), amount = 0xFF3F78C4.toInt(),
            awe = AWE_DEEP, textShadow = false,
            icons = IconColors(
                stroke = 0xFF4A5A6A.toInt(), ink3 = 0xFF75828F.toInt(), sun = 0xFFF0A412.toInt(), cloudFill = 0xFFFFFFFF.toInt(),
                rain = 0xFF3F78C4.toInt(), moonInk = 0xFFFDF2C9.toInt(), moonIcon = 0xFFDFE2E7.toInt(), crater = 0xFFC3C8D0.toInt(),
            ),
        )

        val DARK = WidgetTheme(
            dark = true,
            ink = 0xFFE8EEF4.toInt(), ink2 = 0xFFA8B5C2.toInt(), ink3 = 0xFF7C8996.toInt(),
            accent = 0xFF6EA1FF.toInt(), todayName = 0xFF6EA1FF.toInt(),
            panel = 0x17FFFFFF, today = 0x246EA1FF, rainBlock = 0xFF7FB0F0.toInt(), amount = 0xFF7FB0F0.toInt(),
            awe = AWE_BRIGHT, textShadow = false,
            icons = IconColors(
                stroke = 0xFFA8B5C2.toInt(), ink3 = 0xFF7C8996.toInt(), sun = 0xFFFFC24A.toInt(), cloudFill = 0xFF2B3644.toInt(),
                rain = 0xFF7FB0F0.toInt(), moonInk = 0xFFF1E9C6.toInt(), moonIcon = 0xFFB8BCC3.toInt(), crater = 0xFF969BA4.toInt(),
            ),
        )

        /** White text with a soft shadow, for a see-through widget over a dark wallpaper. */
        val ON_DARK_WALLPAPER = WidgetTheme(
            dark = true,
            ink = 0xFFFFFFFF.toInt(), ink2 = 0xFFE6EDF5.toInt(), ink3 = 0xFFCFD9E4.toInt(),
            accent = 0xFF6EA1FF.toInt(), todayName = 0xFFFFFFFF.toInt(),
            panel = 0x4D0C1622, today = 0x24FFFFFF, rainBlock = 0xFF7FB0F0.toInt(), amount = 0xFFD6E7FF.toInt(),
            awe = AWE_BRIGHT, textShadow = true,
            icons = IconColors(
                stroke = 0xFFE6EDF5.toInt(), ink3 = 0xFFCFD9E4.toInt(), sun = 0xFFFFC24A.toInt(), cloudFill = 0xFFF2F5F9.toInt(),
                rain = 0xFF9CC4F5.toInt(), moonInk = 0xFFF1E9C6.toInt(), moonIcon = 0xFFDFE2E7.toInt(), crater = 0xFFC3C8D0.toInt(),
            ),
        )

        /** Once the background is this see-through, text follows the wallpaper instead of the sky. */
        const val WALLPAPER_TEXT_FROM = 40

        fun pick(phoneDark: Boolean, transparency: Int, wallpaperSupportsDarkText: Boolean): WidgetTheme = when {
            transparency < WALLPAPER_TEXT_FROM -> if (phoneDark) DARK else LIGHT
            wallpaperSupportsDarkText -> LIGHT
            else -> ON_DARK_WALLPAPER
        }
    }
}
