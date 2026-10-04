package com.hong.volace.data

import com.hong.volace.audio.DndMode
import android.content.Context
import androidx.annotation.StringRes
import com.hong.volace.R
import com.hong.volace.audio.VolumeStream
import com.hong.volace.audio.isKeptBy
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

/**
 * Profiles as a JSON file, to copy them to another phone or keep them across a reinstall.
 *
 * Only what defines a profile is written: no database ids, no "applied" flag, no order index
 * (the list order is the order). Reading checks every field, so a broken or foreign file is
 * refused with a message rather than half imported.
 */
object ProfileBackup {

    private const val FORMAT = "volace-profiles"
    private const val VERSION = 1

    /** Why a file was refused, as a string resource so the UI can say it in the phone's language. */
    class FormatException(@StringRes val reason: Int, vararg val args: Any) : Exception("format: $reason") {
        fun describe(context: Context): String = context.getString(reason, *args)
    }

    fun toJson(profiles: List<Profile>): String {
        val list = JSONArray()
        profiles.forEach { p ->
            list.put(
                JSONObject()
                    .put("name", p.name)
                    .put("ringerMode", p.ringerMode)
                    .put("ring", p.ringVolume)
                    .put("notification", p.notificationVolume)
                    .put("media", p.mediaVolume)
                    .put("alarm", p.alarmVolume)
                    .put("voiceCall", p.voiceCallVolume)
                    .put("system", p.systemVolume)
                    .put("color", p.colorArgb)
                    .put("icon", p.iconKey)
                    .put(
                        "keep",
                        JSONArray(VolumeStream.entries.filter { it.isKeptBy(p) }.map { it.key }),
                    )
                    // Not the sounds: their URIs are this phone's (DESIGN.md 5.16).
                    .put("dnd", DndMode.of(p.dndMode).key),
            )
        }
        return JSONObject()
            .put("format", FORMAT)
            .put("version", VERSION)
            .put("profiles", list)
            .toString(2)
    }

    /**
     * Profiles in file order, not yet stored: ids 0, nothing applied, [firstOrder] onwards.
     * Levels are kept as written; the caller moves them into this device's ranges.
     */
    fun fromJson(text: String, firstOrder: Int = 0): List<Profile> {
        val root = try {
            JSONObject(text)
        } catch (e: JSONException) {
            throw FormatException(R.string.backup_err_not_json)
        }
        if (root.optString("format") != FORMAT) throw FormatException(R.string.backup_err_not_volace)
        val version = root.optInt("version", -1)
        if (version !in 1..VERSION) throw FormatException(R.string.backup_err_version, version)
        val list = root.optJSONArray("profiles") ?: throw FormatException(R.string.backup_err_no_profiles)

        return (0 until list.length()).map { i ->
            val o = list.optJSONObject(i) ?: throw FormatException(R.string.backup_err_unreadable, i + 1)
            fun int(key: String): Int =
                if (o.has(key)) o.optInt(key, Int.MIN_VALUE).takeIf { it != Int.MIN_VALUE }
                    ?: throw FormatException(R.string.backup_err_not_number, i + 1, key)
                else throw FormatException(R.string.backup_err_missing, i + 1, key)
            val name = o.optString("name").takeIf { it.isNotBlank() }
                ?: throw FormatException(R.string.backup_err_no_name, i + 1)
            Profile(
                name = name,
                orderIndex = firstOrder + i,
                ringerMode = int("ringerMode").takeIf { it in 0..2 }
                    ?: throw FormatException(R.string.backup_err_ringer, i + 1),
                ringVolume = int("ring"),
                notificationVolume = int("notification"),
                mediaVolume = int("media"),
                alarmVolume = int("alarm"),
                voiceCallVolume = int("voiceCall"),
                systemVolume = int("system"),
                colorArgb = int("color"),
                // Unknown icons fall back to the default when drawn (ProfileIcon.fromKey).
                iconKey = o.optString("icon", ProfileIcon.DEFAULT.key),
                keepMask = keepMask(o.optJSONArray("keep")),
                // Absent in older files; an unknown value is "使わない".
                dndMode = DndMode.ofKey(o.optString("dnd")).value,
            )
        }
    }

    /** Unknown names are ignored, so a newer file with more streams still loads. */
    private fun keepMask(keep: JSONArray?): Int {
        if (keep == null) return 0
        val byKey = VolumeStream.entries.associateBy { it.key }
        return (0 until keep.length()).fold(0) { mask, i -> mask or (byKey[keep.optString(i)]?.keepBit ?: 0) }
    }
}
