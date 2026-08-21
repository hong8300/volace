package com.hong.volace.data

import androidx.annotation.DrawableRes
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
enum class ProfileIcon(val key: String, @DrawableRes val res: Int, val label: String) {
    VOLUME_UP("volume_up", R.drawable.ic_profile_volume_up, "音量"),
    VOLUME_OFF("volume_off", R.drawable.ic_profile_volume_off, "消音"),
    VIBRATION("vibration", R.drawable.ic_profile_vibration, "バイブ"),
    BELL("bell", R.drawable.ic_profile_bell, "着信"),
    MUSIC("music", R.drawable.ic_profile_music, "音楽"),
    HEADPHONES("headphones", R.drawable.ic_profile_headphones, "ヘッドホン"),
    ALARM("alarm", R.drawable.ic_profile_alarm, "アラーム"),
    NIGHT("night", R.drawable.ic_profile_night, "就寝"),
    WORK("work", R.drawable.ic_profile_work, "仕事"),
    HOME("home", R.drawable.ic_profile_home, "自宅"),
    CAR("car", R.drawable.ic_profile_car, "運転"),
    RESTAURANT("restaurant", R.drawable.ic_profile_restaurant, "食事"),
    FLIGHT("flight", R.drawable.ic_profile_flight, "移動"),
    STAR("star", R.drawable.ic_profile_star, "お気に入り");

    companion object {
        val DEFAULT = VOLUME_UP

        fun fromKey(key: String?): ProfileIcon = entries.firstOrNull { it.key == key } ?: DEFAULT
    }
}

val Profile.icon: ProfileIcon get() = ProfileIcon.fromKey(iconKey)
