package com.hong.volace.ui.theme

import android.content.Context
import androidx.annotation.StringRes
import com.hong.volace.R
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * The look of the app and its widgets. [key] is stored: never rename it.
 * Light/dark of [AUTO] and [DYNAMIC] follow the system; the others are fixed.
 */
enum class Skin(val key: String, @StringRes val label: Int, @StringRes val description: Int) {
    DEFAULT("default", R.string.skin_default, R.string.skin_default_desc),
    LIGHT("light", R.string.skin_light, R.string.skin_light_desc),
    AUTO("auto", R.string.skin_auto, R.string.skin_auto_desc),
    DYNAMIC("dynamic", R.string.skin_dynamic, R.string.skin_dynamic_desc),
    HIGH_CONTRAST("contrast", R.string.skin_contrast, R.string.skin_contrast_desc),
    MIDNIGHT("midnight", R.string.skin_midnight, R.string.skin_midnight_desc),
    ;

    companion object {
        fun fromKey(key: String?): Skin = entries.firstOrNull { it.key == key } ?: DEFAULT
    }
}

/** The chosen skin, kept in preferences (backed up with the rest of the settings). */
object SkinStore {
    private const val PREFS = "volace"
    private const val KEY = "skin"

    private var flow: MutableStateFlow<Skin>? = null

    fun current(context: Context): Skin = state(context).value

    /** For Compose: the screen recolours as soon as another skin is picked. */
    fun state(context: Context): StateFlow<Skin> =
        flow ?: synchronized(this) {
            flow ?: MutableStateFlow(
                Skin.fromKey(context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, null)),
            ).also { flow = it }
        }

    fun set(context: Context, skin: Skin) {
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY, skin.key).apply()
        (state(context) as MutableStateFlow).value = skin
    }
}
