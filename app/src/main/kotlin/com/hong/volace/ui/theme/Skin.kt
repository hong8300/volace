package com.hong.volace.ui.theme

import android.content.Context
import androidx.annotation.StringRes
import androidx.core.content.edit
import com.hong.volace.R
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** How the skin list is grouped in the settings. */
enum class SkinGroup(@StringRes val label: Int) {
    BASIC(R.string.skin_group_basic),

    /** Soft light colours, rounder shapes (Skin.rounded). */
    PASTEL(R.string.skin_group_pastel),
    DARK(R.string.skin_group_dark),
}

/**
 * The look of the app and its widgets. [key] is stored: never rename it.
 * Light/dark of [AUTO] and [DYNAMIC] follow the system; the others are fixed. The first six have
 * hand-made colour schemes (Theme.kt, WidgetPalette); the rest are made from a [SkinColors].
 */
enum class Skin(
    val key: String,
    @StringRes val label: Int,
    @StringRes val description: Int,
    val group: SkinGroup,
    val colors: SkinColors? = null,
) {
    DEFAULT("default", R.string.skin_default, R.string.skin_default_desc, SkinGroup.BASIC),
    LIGHT("light", R.string.skin_light, R.string.skin_light_desc, SkinGroup.BASIC),
    AUTO("auto", R.string.skin_auto, R.string.skin_auto_desc, SkinGroup.BASIC),
    DYNAMIC("dynamic", R.string.skin_dynamic, R.string.skin_dynamic_desc, SkinGroup.BASIC),
    HIGH_CONTRAST("contrast", R.string.skin_contrast, R.string.skin_contrast_desc, SkinGroup.BASIC),
    PAPER("paper", R.string.skin_paper, R.string.skin_paper_desc, SkinGroup.BASIC, SkinColors.PAPER),

    SAKURA("sakura", R.string.skin_sakura, R.string.skin_sakura_desc, SkinGroup.PASTEL, SkinColors.SAKURA),
    PEACH("peach", R.string.skin_peach, R.string.skin_peach_desc, SkinGroup.PASTEL, SkinColors.PEACH),
    LEMON("lemon", R.string.skin_lemon, R.string.skin_lemon_desc, SkinGroup.PASTEL, SkinColors.LEMON),
    MINT("mint", R.string.skin_mint, R.string.skin_mint_desc, SkinGroup.PASTEL, SkinColors.MINT),
    SKY("sky", R.string.skin_sky, R.string.skin_sky_desc, SkinGroup.PASTEL, SkinColors.SKY),
    LAVENDER("lavender", R.string.skin_lavender, R.string.skin_lavender_desc, SkinGroup.PASTEL, SkinColors.LAVENDER),
    MILK_TEA("milk_tea", R.string.skin_milk_tea, R.string.skin_milk_tea_desc, SkinGroup.PASTEL, SkinColors.MILK_TEA),
    DREAMY("dreamy", R.string.skin_dreamy, R.string.skin_dreamy_desc, SkinGroup.PASTEL, SkinColors.DREAMY),

    MIDNIGHT("midnight", R.string.skin_midnight, R.string.skin_midnight_desc, SkinGroup.DARK),
    FOREST("forest", R.string.skin_forest, R.string.skin_forest_desc, SkinGroup.DARK, SkinColors.FOREST),
    COCOA("cocoa", R.string.skin_cocoa, R.string.skin_cocoa_desc, SkinGroup.DARK, SkinColors.COCOA),
    WINE("wine", R.string.skin_wine, R.string.skin_wine_desc, SkinGroup.DARK, SkinColors.WINE),
    SUMI("sumi", R.string.skin_sumi, R.string.skin_sumi_desc, SkinGroup.DARK, SkinColors.SUMI),
    ;

    /** Pastel skins draw rounder: cards, dialogs and the widgets' cells (DESIGN.md 5.13). */
    val rounded: Boolean get() = group == SkinGroup.PASTEL

    companion object {
        fun fromKey(key: String?): Skin = entries.firstOrNull { it.key == key } ?: DEFAULT
    }
}

/**
 * A skin's colours, from which both the app's colour scheme (Theme.kt) and the widgets' palette
 * (WidgetPalette) are made. ARGB.
 */
data class SkinColors(
    val dark: Boolean,
    val background: Int,
    /** Cards. */
    val surface: Int,
    /** Raised cards, chips, the "選ぶ" tile. */
    val surfaceHigh: Int,
    val primary: Int,
    val onPrimary: Int,
    val primaryContainer: Int,
    val onPrimaryContainer: Int,
    val secondary: Int,
    val text: Int,
    val subText: Int,
    val outline: Int,
    val outlineVariant: Int,
) {
    companion object {
        private fun light(
            background: Long, surface: Long, surfaceHigh: Long, primary: Long, primaryContainer: Long,
            onPrimaryContainer: Long, secondary: Long, text: Long, subText: Long, outline: Long, outlineVariant: Long,
        ) = SkinColors(
            dark = false, background = background.toInt(), surface = surface.toInt(), surfaceHigh = surfaceHigh.toInt(),
            primary = primary.toInt(), onPrimary = 0xFFFFFFFF.toInt(), primaryContainer = primaryContainer.toInt(),
            onPrimaryContainer = onPrimaryContainer.toInt(), secondary = secondary.toInt(), text = text.toInt(),
            subText = subText.toInt(), outline = outline.toInt(), outlineVariant = outlineVariant.toInt(),
        )

        private fun dark(
            background: Long, surface: Long, surfaceHigh: Long, primary: Long, onPrimary: Long, primaryContainer: Long,
            onPrimaryContainer: Long, secondary: Long, text: Long, subText: Long, outline: Long, outlineVariant: Long,
        ) = SkinColors(
            dark = true, background = background.toInt(), surface = surface.toInt(), surfaceHigh = surfaceHigh.toInt(),
            primary = primary.toInt(), onPrimary = onPrimary.toInt(), primaryContainer = primaryContainer.toInt(),
            onPrimaryContainer = onPrimaryContainer.toInt(), secondary = secondary.toInt(), text = text.toInt(),
            subText = subText.toInt(), outline = outline.toInt(), outlineVariant = outlineVariant.toInt(),
        )

        val PAPER = light(0xFFFAFAF7, 0xFFF0F0EC, 0xFFE6E6E0, 0xFF4A4A4A, 0xFFE0E0DA, 0xFF1A1A1A, 0xFF6B6B6B, 0xFF1C1C1C, 0xFF5C5C5C, 0xFFC8C8C2, 0xFFE0E0DA)

        val SAKURA = light(0xFFFFF5F8, 0xFFFFE9F0, 0xFFFFDDE8, 0xFFD94F82, 0xFFFFD6E5, 0xFF5C1033, 0xFFC76B98, 0xFF3A2430, 0xFF7A5866, 0xFFE8B9CB, 0xFFF5D6E2)
        val PEACH = light(0xFFFFF6F1, 0xFFFFEADF, 0xFFFFDDCD, 0xFFCF6A44, 0xFFFFDCCB, 0xFF5A220C, 0xFFE8956B, 0xFF3D2A22, 0xFF7D5E52, 0xFFF0C1AC, 0xFFF8DCCF)
        val LEMON = light(0xFFFFFCEB, 0xFFFFF6CC, 0xFFFFEFB3, 0xFF9C7400, 0xFFFFEB99, 0xFF3D2E00, 0xFFD9A400, 0xFF3A3420, 0xFF75694A, 0xFFE6D58F, 0xFFF3E8B8)
        val MINT = light(0xFFF2FBF7, 0xFFE3F6EE, 0xFFD3F0E3, 0xFF23876A, 0xFFC8EFE0, 0xFF0B3D2E, 0xFF3FA7A0, 0xFF1F3A31, 0xFF557568, 0xFFA9DCC8, 0xFFCDEBDF)
        val SKY = light(0xFFF3F9FF, 0xFFE3F1FF, 0xFFD3E8FF, 0xFF2F7CC9, 0xFFD2E6FF, 0xFF0D2F57, 0xFF4FA9C9, 0xFF1E2D3D, 0xFF4E6680, 0xFFA9CDEE, 0xFFCFE3F7)
        val LAVENDER = light(0xFFF7F4FF, 0xFFEEE8FF, 0xFFE4DBFF, 0xFF7354CC, 0xFFE3D9FF, 0xFF2A1868, 0xFFA06CD5, 0xFF2D2640, 0xFF6A6085, 0xFFC9BCEE, 0xFFE2D9F7)
        val MILK_TEA = light(0xFFFBF6F0, 0xFFF2E8DC, 0xFFEADBCB, 0xFF8C5F40, 0xFFEED8C4, 0xFF3B2414, 0xFFB08968, 0xFF3B2E25, 0xFF75625A, 0xFFD8C2AE, 0xFFEBDCCD)

        /** "ゆめかわ": pastel pink and aqua on a deep lilac night. */
        val DREAMY = dark(0xFF1E1A2E, 0xFF29233D, 0xFF332B4A, 0xFFFFB8E0, 0xFF4A1236, 0xFF5E3A6E, 0xFFFFE0F2, 0xFF9EE6F0, 0xFFF3EDFF, 0xFFC8BEE0, 0xFF55497A, 0xFF3A3155)

        val FOREST = dark(0xFF0F1A14, 0xFF16241C, 0xFF1D2E24, 0xFF8FD6A8, 0xFF003920, 0xFF1F5136, 0xFFC4F2D3, 0xFF9AD0C2, 0xFFE3F1E8, 0xFFA9C3B3, 0xFF3F5A4A, 0xFF2A3D32)
        val COCOA = dark(0xFF1A1411, 0xFF241B17, 0xFF2E231E, 0xFFE6B98E, 0xFF3F2711, 0xFF5A3B22, 0xFFFFDDBF, 0xFFD9A98A, 0xFFF3E9E2, 0xFFCDB9AC, 0xFF5A473D, 0xFF3C2F28)
        val WINE = dark(0xFF1C1016, 0xFF28161F, 0xFF331C28, 0xFFF2A5C5, 0xFF4D0F2C, 0xFF6B2547, 0xFFFFD8E8, 0xFFE8A0B8, 0xFFF7E6EE, 0xFFD3B3C2, 0xFF5E3B4B, 0xFF3F2632)
        val SUMI = dark(0xFF141414, 0xFF1E1E1E, 0xFF282828, 0xFFD0D0D0, 0xFF1A1A1A, 0xFF3A3A3A, 0xFFF0F0F0, 0xFFA8A8A8, 0xFFEDEDED, 0xFFB5B5B5, 0xFF4A4A4A, 0xFF323232)
    }
}

/**
 * How profiles' icons are drawn everywhere (list, widgets, picker, shortcuts): the line icons, or
 * emoji ("かわいい"). Independent of the skin's colours. [key] is stored: never rename it.
 */
enum class IconStyle(val key: String, @StringRes val label: Int) {
    STANDARD("standard", R.string.icon_style_standard),
    EMOJI("emoji", R.string.icon_style_emoji),
    ;

    companion object {
        fun fromKey(key: String?): IconStyle = entries.firstOrNull { it.key == key } ?: STANDARD
    }
}

/** The chosen skin and icon style, kept in preferences (backed up with the rest of the settings). */
object SkinStore {
    private const val PREFS = "volace"
    private const val KEY = "skin"
    private const val KEY_ICONS = "icon_style"

    private var flow: MutableStateFlow<Skin>? = null
    private var iconFlow: MutableStateFlow<IconStyle>? = null

    private fun prefs(context: Context) = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun current(context: Context): Skin = state(context).value

    /** For Compose: the screen recolours as soon as another skin is picked. */
    fun state(context: Context): StateFlow<Skin> =
        flow ?: synchronized(this) {
            flow ?: MutableStateFlow(Skin.fromKey(prefs(context).getString(KEY, null))).also { flow = it }
        }

    fun set(context: Context, skin: Skin) {
        prefs(context).edit { putString(KEY, skin.key) }
        (state(context) as MutableStateFlow).value = skin
    }

    fun iconStyle(context: Context): IconStyle = iconState(context).value

    fun iconState(context: Context): StateFlow<IconStyle> =
        iconFlow ?: synchronized(this) {
            iconFlow ?: MutableStateFlow(IconStyle.fromKey(prefs(context).getString(KEY_ICONS, null))).also { iconFlow = it }
        }

    fun setIconStyle(context: Context, style: IconStyle) {
        prefs(context).edit { putString(KEY_ICONS, style.key) }
        (iconState(context) as MutableStateFlow).value = style
    }
}
