package com.hong.volace.widget

import com.hong.volace.ui.theme.SkinColors
import android.content.Context
import com.hong.volace.ui.theme.Skin

/**
 * Widget colours for a skin, as a light and a dark set. The launcher picks between them by its
 * own night mode (RemoteViews.setColorInt), so AUTO and DYNAMIC follow the system without a
 * redraw; fixed skins simply give the same set twice.
 */
class WidgetPalette(val day: Colors, val night: Colors) {

    data class Colors(
        val background: Int,
        /** Status strip / "選ぶ" tile on top of the background. */
        val panel: Int,
        val text: Int,
        /** Stream names, captions. */
        val subText: Int,
        /** Bar tracks (only the colour counts; the drawable keeps its own transparency). */
        val track: Int,
        /** Bars and "アプリを開く" when no profile is applied (otherwise the profile's colour). */
        val accent: Int,
        /** The "追加" cell when there are no profiles. */
        val emptyCell: Int,
        /** Light backgrounds need profile colours darkened rather than lightened. */
        val isLight: Boolean,
    )

    companion object {
        private val DARK = Colors(
            background = 0xD912121C.toInt(), panel = 0x1FFFFFFF, text = 0xFFFFFFFF.toInt(),
            subText = 0xFFDCDCE6.toInt(), track = 0xFFFFFFFF.toInt(), accent = 0xFF8FB8FF.toInt(),
            emptyCell = 0xFF3B3B4A.toInt(), isLight = false,
        )
        private val LIGHT = Colors(
            background = 0xF2F7F8FC.toInt(), panel = 0x14000000, text = 0xFF15161C.toInt(),
            subText = 0xFF474A55.toInt(), track = 0xFF000000.toInt(), accent = 0xFF2E6DE0.toInt(),
            emptyCell = 0xFFDDE1EA.toInt(), isLight = true,
        )
        private val CONTRAST = Colors(
            background = 0xFF000000.toInt(), panel = 0xFF1C1C1C.toInt(), text = 0xFFFFFFFF.toInt(),
            subText = 0xFFFFFFFF.toInt(), track = 0xFFFFFFFF.toInt(), accent = 0xFFFFE066.toInt(),
            emptyCell = 0xFF303030.toInt(), isLight = false,
        )
        private val MIDNIGHT = Colors(
            background = 0xF20B1730.toInt(), panel = 0x248FB8FF, text = 0xFFEAF1FF.toInt(),
            subText = 0xFFB8C7E8.toInt(), track = 0xFF8FB8FF.toInt(), accent = 0xFF9CC2FF.toInt(),
            emptyCell = 0xFF1F3158.toInt(), isLight = false,
        )

        fun of(context: Context, skin: Skin): WidgetPalette = when (skin) {
            Skin.DEFAULT -> WidgetPalette(DARK, DARK)
            Skin.LIGHT -> WidgetPalette(LIGHT, LIGHT)
            Skin.AUTO -> WidgetPalette(LIGHT, DARK)
            Skin.HIGH_CONTRAST -> WidgetPalette(CONTRAST, CONTRAST)
            Skin.MIDNIGHT -> WidgetPalette(MIDNIGHT, MIDNIGHT)
            Skin.DYNAMIC -> dynamic(context)
            else -> skin.colors?.let { fromTable(it) }?.let { WidgetPalette(it, it) } ?: WidgetPalette(DARK, DARK)
        }

        /** The widget set of a skin made from a colour table (the app uses the same table, Theme.kt). */
        internal fun fromTable(c: SkinColors) = Colors(
            background = withAlpha(c.background, 0xF2),
            panel = withAlpha(c.primary, if (c.dark) 0x24 else 0x1A),
            text = c.text,
            subText = c.subText,
            track = c.text,
            accent = c.primary,
            emptyCell = c.surfaceHigh,
            isLight = !c.dark,
        )

        /** Material You: the wallpaper's tonal palette, read now (a wallpaper change shows on the next redraw). */
        private fun dynamic(context: Context): WidgetPalette {
            fun c(id: Int) = context.getColor(id)
            val day = Colors(
                background = withAlpha(c(android.R.color.system_neutral1_50), 0xF2),
                panel = withAlpha(c(android.R.color.system_accent2_100), 0xCC),
                text = c(android.R.color.system_neutral1_900),
                subText = c(android.R.color.system_neutral2_700),
                track = c(android.R.color.system_neutral2_900),
                accent = c(android.R.color.system_accent1_600),
                emptyCell = c(android.R.color.system_neutral2_100),
                isLight = true,
            )
            val night = Colors(
                background = withAlpha(c(android.R.color.system_neutral1_900), 0xF2),
                panel = withAlpha(c(android.R.color.system_accent2_800), 0xCC),
                text = c(android.R.color.system_neutral1_50),
                subText = c(android.R.color.system_neutral2_200),
                track = c(android.R.color.system_neutral2_50),
                accent = c(android.R.color.system_accent1_200),
                emptyCell = c(android.R.color.system_neutral2_800),
                isLight = false,
            )
            return WidgetPalette(day, night)
        }

        private fun withAlpha(color: Int, alpha: Int): Int = (color and 0x00FFFFFF) or (alpha shl 24)
    }
}
