package com.hong.volace.timer

import android.content.Context
import android.content.SharedPreferences
import android.media.AudioManager
import androidx.core.content.edit
import com.hong.volace.audio.DeviceVolumes
import com.hong.volace.audio.SoundKind
import com.hong.volace.audio.VolumeStream
import com.hong.volace.audio.copyWith
import com.hong.volace.audio.keepIn
import com.hong.volace.data.Profile
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import org.json.JSONObject
import java.util.Calendar

/**
 * A profile applied for a while ("1 時間だけサイレント") and what comes back when the time is up.
 *
 * [previous] is always recorded, even when [restoreId] names a profile: if that profile is deleted
 * in the meantime, the state from before the timer is the safest thing to go back to.
 */
data class ProfileTimer(
    val profileId: Long,
    /** Wall-clock time (ms) the timer ends. */
    val endAt: Long,
    /** Profile to apply at [endAt]; null goes back to [previous]. */
    val restoreId: Long?,
    /** Ringer mode and volumes just before the timer started. */
    val previous: DeviceVolumes,
    /** The profile marked as in effect before the timer, marked again when [previous] comes back. */
    val previousActiveId: Long?,
    /** Names as they were at the start, for the notification (which cannot wait for the database). */
    val profileName: String,
    val restoreName: String,
    /** The default sounds before the timer, for the kinds the timer's profile changes. */
    val previousSounds: Map<SoundKind, String> = emptyMap(),
) {
    /**
     * The time is up. A timer still stored after that is one whose restore Android refused (e.g.
     * after a reboot, without the service); it waits for the user to restore it.
     */
    fun isDue(now: Long): Boolean = now >= endAt

    fun toJson(): String = JSONObject()
        .put("profileId", profileId)
        .put("endAt", endAt)
        .put("restoreId", restoreId ?: JSONObject.NULL)
        .put("previousActiveId", previousActiveId ?: JSONObject.NULL)
        .put("profileName", profileName)
        .put("restoreName", restoreName)
        .put("ringerMode", previous.ringerMode)
        .put("dnd", previous.volaceDnd)
        .put("levels", JSONObject().apply { previous.levels.forEach { (stream, level) -> put(stream.key, level) } })
        .put("sounds", JSONObject().apply { previousSounds.forEach { (kind, value) -> put(kind.key, value) } })
        .toString()

    /** What "適用前の状態に戻す" writes: the volumes from before, and the sounds the timer changed. */
    fun previousProfile(name: String): Profile =
        previousSounds.entries.fold(previous.asProfile(name)) { profile, (kind, value) -> kind.copyWith(profile, value) }

    companion object {
        /** Null for anything unreadable: a broken record must not keep a timer alive. */
        fun fromJson(text: String): ProfileTimer? = runCatching {
            val json = JSONObject(text)
            val levels = json.getJSONObject("levels")
            ProfileTimer(
                profileId = json.getLong("profileId"),
                endAt = json.getLong("endAt"),
                restoreId = json.optLongOrNull("restoreId"),
                previous = DeviceVolumes(
                    ringerMode = json.getInt("ringerMode"),
                    levels = VolumeStream.entries.associateWith { levels.optInt(it.key, 0) },
                    volaceDnd = json.optInt("dnd", 0),
                ),
                previousActiveId = json.optLongOrNull("previousActiveId"),
                profileName = json.optString("profileName"),
                restoreName = json.optString("restoreName"),
                // Absent in timers saved before sounds existed.
                previousSounds = json.optJSONObject("sounds")?.let { sounds ->
                    SoundKind.entries.filter { sounds.has(it.key) }.associateWith { sounds.getString(it.key) }
                }.orEmpty(),
            )
        }.getOrNull()

        private fun JSONObject.optLongOrNull(name: String): Long? = if (isNull(name)) null else getLong(name)
    }
}

/**
 * [DeviceVolumes] as a profile [com.hong.volace.audio.VolumeApplier.apply] can write back.
 * In vibrate/silent the ringer and notification streams read 0 because they are muted, and SYSTEM
 * follows the ringer; writing those zeros would lose the levels the device returns to when the
 * ringer is turned back on, so they are left alone ("変更しない").
 */
internal fun DeviceVolumes.asProfile(name: String): Profile {
    val base = Profile(
        name = name,
        orderIndex = 0,
        ringerMode = ringerMode,
        ringVolume = 0,
        notificationVolume = 0,
        mediaVolume = 0,
        alarmVolume = 0,
        voiceCallVolume = 0,
        systemVolume = 0,
        // Volace's own DND mode as it was; another app's is not Volace's to bring back.
        dndMode = volaceDnd,
    )
    val withLevels = VolumeStream.entries.fold(base) { p, stream -> stream.copyWith(p, levelOf(stream)) }
    if (ringerMode == AudioManager.RINGER_MODE_NORMAL) return withLevels
    return listOf(VolumeStream.RINGER, VolumeStream.NOTIFICATION, VolumeStream.SYSTEM)
        .fold(withLevels) { p, stream -> stream.keepIn(p, keep = true) }
}

/**
 * The next time the clock shows [hour]:[minute] after [now]: today if that is still ahead,
 * otherwise tomorrow ("7:00 まで" chosen at 23:00 means the next morning).
 */
internal fun nextOccurrence(now: Long, hour: Int, minute: Int): Long {
    val calendar = Calendar.getInstance().apply {
        timeInMillis = now
        set(Calendar.HOUR_OF_DAY, hour)
        set(Calendar.MINUTE, minute)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }
    if (calendar.timeInMillis <= now) calendar.add(Calendar.DAY_OF_YEAR, 1)
    return calendar.timeInMillis
}

/** The one timer, if any. Device-local (volace_device.xml is excluded from backups). */
internal object TimerStore {

    private const val PREFS = "volace_device"
    private const val KEY = "timer"

    private fun prefs(context: Context): SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun load(context: Context): ProfileTimer? = prefs(context).getString(KEY, null)?.let(ProfileTimer::fromJson)

    // Committed at once (not apply()): the alarm and the service read it right after, and the
    // process may be killed soon after a background write.
    fun save(context: Context, timer: ProfileTimer) {
        prefs(context).edit(commit = true) { putString(KEY, timer.toJson()) }
    }

    fun clear(context: Context) {
        prefs(context).edit(commit = true) { remove(KEY) }
    }

    /** The timer now and after every change, for screens that show it. */
    fun observe(context: Context): Flow<ProfileTimer?> = callbackFlow {
        val prefs = prefs(context)
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == KEY) trySend(load(context))
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        trySend(load(context))
        awaitClose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }
}
