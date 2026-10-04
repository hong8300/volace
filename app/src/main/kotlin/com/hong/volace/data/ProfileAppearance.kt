package com.hong.volace.data

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.core.graphics.ColorUtils
import com.hong.volace.R

/** Selectable accent colours. Stored on the profile as a plain ARGB int. */
object ProfilePalette {
    val COLORS: List<Int> = listOf(
        0xFF2196F3.toInt(), // blue
        0xFF26A69A.toInt(), // teal
        0xFF66BB6A.toInt(), // green
        0xFFFFB300.toInt(), // amber
        0xFFFF7043.toInt(), // deep orange
        0xFFEF5350.toInt(), // red
        0xFFEC407A.toInt(), // pink
        0xFF7E57C2.toInt(), // purple
        0xFF5C6BC0.toInt(), // indigo
        0xFF78909C.toInt(), // blue grey
        // Added in #62: pastels (light enough for dark text, contentColorOn) and deeper tones.
        0xFFF8BBD0.toInt(), // sakura
        0xFFFFAB91.toInt(), // coral
        0xFFFFCC80.toInt(), // peach
        0xFFFFE082.toInt(), // cream
        0xFFDCE775.toInt(), // lime
        0xFFA5D6A7.toInt(), // young green
        0xFF80CBC4.toInt(), // mint
        0xFF90CAF9.toInt(), // sky
        0xFFB4BEF5.toInt(), // periwinkle
        0xFFC8B6F0.toInt(), // lavender
        0xFFE2B6EC.toInt(), // lilac
        0xFFD7CCC8.toInt(), // milk tea
        0xFF8D6E63.toInt(), // brown
        0xFF546E7A.toInt(), // charcoal
        0xFFAD1457.toInt(), // wine
        0xFF283593.toInt(), // navy
        0xFF2E7D32.toInt(), // forest
        0xFF00838F.toInt(), // deep cyan
    )

    /** The first ten, from before #62 (they keep white text but amber; ContrastTest). */
    const val ORIGINAL_COUNT = 10

    /** Names for TalkBack, same order as [COLORS]. */
    val NAMES: List<Int> = listOf(
        R.string.color_blue, R.string.color_teal, R.string.color_green, R.string.color_amber,
        R.string.color_deep_orange, R.string.color_red, R.string.color_pink, R.string.color_purple,
        R.string.color_indigo, R.string.color_blue_grey,
        R.string.color_sakura, R.string.color_coral, R.string.color_peach, R.string.color_cream,
        R.string.color_lime, R.string.color_young_green, R.string.color_mint, R.string.color_sky,
        R.string.color_periwinkle, R.string.color_lavender, R.string.color_lilac, R.string.color_milk_tea,
        R.string.color_brown, R.string.color_charcoal, R.string.color_wine, R.string.color_navy,
        R.string.color_forest, R.string.color_deep_cyan,
    )

    val DEFAULT: Int = COLORS[0]

    fun forIndex(index: Int): Int = COLORS[(index.coerceAtLeast(0)) % COLORS.size]
}

/**
 * Icons a profile can be tagged with. The same vector drawable is used by both the Compose UI and
 * the home-screen widget, so a profile always looks identical in the two places.
 */
enum class ProfileIcon(
    val key: String,
    @DrawableRes val res: Int,
    @StringRes val label: Int,
    /** What the "かわいい" icon style shows instead (IconStyle.EMOJI). */
    val emoji: String,
) {
    VOLUME_UP("volume_up", R.drawable.ic_profile_volume_up, R.string.icon_volume_up, "🔊"),
    VOLUME_OFF("volume_off", R.drawable.ic_profile_volume_off, R.string.icon_volume_off, "🔇"),
    VIBRATION("vibration", R.drawable.ic_profile_vibration, R.string.icon_vibration, "📳"),
    BELL("bell", R.drawable.ic_profile_bell, R.string.icon_bell, "🔔"),
    MUSIC("music", R.drawable.ic_profile_music, R.string.icon_music, "🎵"),
    HEADPHONES("headphones", R.drawable.ic_profile_headphones, R.string.icon_headphones, "🎧"),
    ALARM("alarm", R.drawable.ic_profile_alarm, R.string.icon_alarm, "⏰"),
    NIGHT("night", R.drawable.ic_profile_night, R.string.icon_night, "🌙"),
    WORK("work", R.drawable.ic_profile_work, R.string.icon_work, "💼"),
    HOME("home", R.drawable.ic_profile_home, R.string.icon_home, "🏠"),
    CAR("car", R.drawable.ic_profile_car, R.string.icon_car, "🚗"),
    RESTAURANT("restaurant", R.drawable.ic_profile_restaurant, R.string.icon_restaurant, "🍽️"),
    FLIGHT("flight", R.drawable.ic_profile_flight, R.string.icon_flight, "✈️"),
    STAR("star", R.drawable.ic_profile_star, R.string.icon_star, "⭐"),

    // Added in #62 ("かわいい").
    CAT("cat", R.drawable.ic_profile_cat, R.string.icon_cat, "🐱"),
    RABBIT("rabbit", R.drawable.ic_profile_rabbit, R.string.icon_rabbit, "🐰"),
    PAW("paw", R.drawable.ic_profile_paw, R.string.icon_paw, "🐾"),
    HEART("heart", R.drawable.ic_profile_heart, R.string.icon_heart, "💗"),
    FLOWER("flower", R.drawable.ic_profile_flower, R.string.icon_flower, "🌸"),
    CLOUD("cloud", R.drawable.ic_profile_cloud, R.string.icon_cloud, "☁️"),
    SPARKLE("sparkle", R.drawable.ic_profile_sparkle, R.string.icon_sparkle, "✨"),
    SUN("sun", R.drawable.ic_profile_sun, R.string.icon_sun, "☀️"),
    SMILE("smile", R.drawable.ic_profile_smile, R.string.icon_smile, "😊"),
    CAKE("cake", R.drawable.ic_profile_cake, R.string.icon_cake, "🍰"),
    COFFEE("coffee", R.drawable.ic_profile_coffee, R.string.icon_coffee, "☕"),
    ICECREAM("icecream", R.drawable.ic_profile_icecream, R.string.icon_icecream, "🍦"),
    BEE("bee", R.drawable.ic_profile_bee, R.string.icon_bee, "🐝"),
    PARTY("party", R.drawable.ic_profile_party, R.string.icon_party, "🎉"),
    TREE("tree", R.drawable.ic_profile_tree, R.string.icon_tree, "🌳"),
    BEACH("beach", R.drawable.ic_profile_beach, R.string.icon_beach, "🏖️");

    companion object {
        val DEFAULT = VOLUME_UP

        fun fromKey(key: String?): ProfileIcon = entries.firstOrNull { it.key == key } ?: DEFAULT
    }
}

val Profile.icon: ProfileIcon get() = ProfileIcon.fromKey(iconKey)

/**
 * Text / icon colour that stays readable on [background]: white on most profile colours, near
 * black on light ones (white on amber #FFB300 is only about 1.8:1).
 */
fun contentColorOn(background: Int): Int =
    if (ColorUtils.calculateLuminance(background) > 0.5) 0xFF1C1B1F.toInt() else 0xFFFFFFFF.toInt()
