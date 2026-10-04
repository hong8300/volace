package com.hong.volace.audio

import android.app.NotificationManager
import android.content.Context
import android.media.AudioManager
import android.util.Log
import com.hong.volace.data.Profile

private const val TAG = "VolumeApplier"

/** What happened when a profile was applied. Only [Applied] may be recorded as "in effect". */
sealed interface ApplyResult {
    data object Applied : ApplyResult

    /** "Do Not Disturb" access was revoked: nothing was changed. */
    data object NeedsAccess : ApplyResult

    /** Android refused some of the changes, so the profile is only partly in effect. */
    data class Partial(val failed: List<String>) : ApplyResult
}

/** One line for a toast or snackbar. */
fun ApplyResult.message(profileName: String): String = when (this) {
    ApplyResult.Applied -> "「$profileName」を適用しました"
    ApplyResult.NeedsAccess ->
        "「サイレント モードへのアクセス」が許可されていないため適用できません。Volace を開いて許可してください"
    is ApplyResult.Partial ->
        "「$profileName」の一部（${failed.joinToString("・")}）を変更できませんでした"
}

class VolumeApplier(context: Context) {
    val context: Context = context.applicationContext
    private val audioManager =
        context.applicationContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val notificationManager =
        context.applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    fun maxVolume(stream: VolumeStream): Int = audioManager.getStreamMaxVolume(stream.streamType)

    fun minVolume(stream: VolumeStream): Int = audioManager.getStreamMinVolume(stream.streamType)

    fun currentVolume(stream: VolumeStream): Int = audioManager.getStreamVolume(stream.streamType)

    private val ranges by lazy { ranges() }

    fun snapshot(): DeviceVolumes = DeviceVolumes(
        ringerMode = audioManager.ringerMode,
        levels = VolumeStream.entries.associateWith { currentVolume(it) },
    )

    /**
     * Whether the device still sits where [profile] left it. Streams Android rewrites on its own
     * are skipped, otherwise a freshly applied profile would immediately look changed:
     * - SYSTEM is aliased to RING (see [apply]), so it always reports the ringer's level.
     * - In vibrate/silent, RING and NOTIFICATION are muted and report 0.
     * Expected values are what [apply] actually writes: moved into the device's range
     * ([StreamRanges]; call and alarm cannot go below 1, an audible ringer not below 1).
     */
    fun matches(profile: Profile, device: DeviceVolumes): Boolean = profileMatches(profile, device, ranges)

    /**
     * Applies [profile] and reports whether Android accepted all of it. Without DND access,
     * changing the ringer mode throws, which used to be swallowed: the volumes were half written
     * and the profile was still recorded as applied. Now nothing is touched in that case.
     */
    fun apply(stored: Profile): ApplyResult {
        if (!notificationManager.isNotificationPolicyAccessGranted) return ApplyResult.NeedsAccess
        // Profiles saved before ranges were enforced (or copied from another phone) may hold
        // levels this device cannot take; write what it can.
        val profile = ranges.normalize(stored)

        val failed = mutableListOf<String>()
        fun attempt(what: String, block: () -> Unit) {
            runCatching(block).onFailure {
                Log.w(TAG, "could not set $what for ${profile.name}", it)
                if (what !in failed) failed += what
            }
        }

        // Set the mode first so the ring/notification streams are unmuted and actually accept the
        // indices we are about to write.
        attempt(RINGER_MODE) { audioManager.ringerMode = profile.ringerMode }

        // STREAM_SYSTEM is aliased to STREAM_RING on stock Android audio policy (confirmed via
        // dumpsys audio on Pixel 9 Pro XL / Android 17): whichever of the two is set last wins.
        // Apply SYSTEM first so the user-facing Ringer value is the one that actually sticks.
        APPLY_ORDER.filterNot { it.isKeptBy(profile) }.forEach { stream ->
            attempt(stream.label) {
                audioManager.setStreamVolume(stream.streamType, stream.valueOf(profile), 0)
            }
        }

        // Writing 0 to STREAM_RING makes the system drop into VIBRATE on its own, which silently
        // overrides an explicit "silent" profile. Re-assert the mode so the profile has the last
        // word, then restore the ring/notification indices the mode change may have bumped.
        attempt(RINGER_MODE) { audioManager.ringerMode = profile.ringerMode }
        if (profile.ringerMode == AudioManager.RINGER_MODE_NORMAL) {
            listOf(VolumeStream.NOTIFICATION, VolumeStream.RINGER).filterNot { it.isKeptBy(profile) }.forEach { stream ->
                attempt(stream.label) {
                    audioManager.setStreamVolume(stream.streamType, stream.valueOf(profile), 0)
                }
            }
        }
        return if (failed.isEmpty()) ApplyResult.Applied else ApplyResult.Partial(failed)
    }

    fun hasAccess(): Boolean = notificationManager.isNotificationPolicyAccessGranted

    private companion object {
        val APPLY_ORDER = listOf(
            VolumeStream.SYSTEM,
            VolumeStream.NOTIFICATION,
            VolumeStream.MEDIA,
            VolumeStream.ALARM,
            VolumeStream.VOICE_CALL,
            VolumeStream.RINGER,
        )

        const val RINGER_MODE = "着信モード"
    }
}
