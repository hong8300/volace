package com.hong.volace.data

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
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
    )

    val DEFAULT: Int = COLORS[0]

    fun forIndex(index: Int): Int = COLORS[(index.coerceAtLeast(0)) % COLORS.size]
}

/**
 * Icons a profile can be tagged with. The same vector drawable is used by both the Compose UI and
 * the home-screen widget, so a profile always looks identical in the two places.
 */
enum class ProfileIcon(val key: String, @DrawableRes val res: Int, @StringRes val label: Int) {
    VOLUME_UP("volume_up", R.drawable.ic_profile_volume_up, R.string.icon_volume_up),
    VOLUME_OFF("volume_off", R.drawable.ic_profile_volume_off, R.string.icon_volume_off),
    VIBRATION("vibration", R.drawable.ic_profile_vibration, R.string.icon_vibration),
    BELL("bell", R.drawable.ic_profile_bell, R.string.icon_bell),
    MUSIC("music", R.drawable.ic_profile_music, R.string.icon_music),
    HEADPHONES("headphones", R.drawable.ic_profile_headphones, R.string.icon_headphones),
    ALARM("alarm", R.drawable.ic_profile_alarm, R.string.icon_alarm),
    NIGHT("night", R.drawable.ic_profile_night, R.string.icon_night),
    WORK("work", R.drawable.ic_profile_work, R.string.icon_work),
    HOME("home", R.drawable.ic_profile_home, R.string.icon_home),
    CAR("car", R.drawable.ic_profile_car, R.string.icon_car),
    RESTAURANT("restaurant", R.drawable.ic_profile_restaurant, R.string.icon_restaurant),
    FLIGHT("flight", R.drawable.ic_profile_flight, R.string.icon_flight),
    STAR("star", R.drawable.ic_profile_star, R.string.icon_star);

    companion object {
        val DEFAULT = VOLUME_UP

        fun fromKey(key: String?): ProfileIcon = entries.firstOrNull { it.key == key } ?: DEFAULT
    }
}

val Profile.icon: ProfileIcon get() = ProfileIcon.fromKey(iconKey)
