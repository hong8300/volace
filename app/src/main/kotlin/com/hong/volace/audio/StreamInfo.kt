package com.hong.volace.audio

import android.media.AudioManager
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Tune
import androidx.compose.ui.graphics.vector.ImageVector
import com.hong.volace.data.Profile

enum class VolumeStream(
    val streamType: Int,
    val label: String,
    /** Single letter shown under the mini bars in the profile list. */
    val shortLabel: String,
    val icon: ImageVector,
    /** This stream's bit in [Profile.keepMask]. Stored in the database: never renumber. */
    val keepBit: Int,
    /** Name in backup files. Never rename. */
    val key: String,
) {
    RINGER(AudioManager.STREAM_RING, "着信音", "R", Icons.Filled.Notifications, 1 shl 0, "ring"),
    NOTIFICATION(AudioManager.STREAM_NOTIFICATION, "通知", "N", Icons.Filled.NotificationsActive, 1 shl 1, "notification"),
    MEDIA(AudioManager.STREAM_MUSIC, "メディア", "M", Icons.Filled.MusicNote, 1 shl 2, "media"),
    ALARM(AudioManager.STREAM_ALARM, "アラーム", "A", Icons.Filled.Alarm, 1 shl 3, "alarm"),
    VOICE_CALL(AudioManager.STREAM_VOICE_CALL, "通話", "V", Icons.Filled.Call, 1 shl 4, "voiceCall"),
    SYSTEM(AudioManager.STREAM_SYSTEM, "システム", "S", Icons.Filled.Tune, 1 shl 5, "system"),
}

/** "変更しない": applying the profile leaves this stream as the device has it. */
fun VolumeStream.isKeptBy(profile: Profile): Boolean = profile.keepMask and keepBit != 0

fun VolumeStream.keepIn(profile: Profile, keep: Boolean): Profile =
    profile.copy(keepMask = if (keep) profile.keepMask or keepBit else profile.keepMask and keepBit.inv())

fun VolumeStream.valueOf(profile: Profile): Int = when (this) {
    VolumeStream.RINGER -> profile.ringVolume
    VolumeStream.NOTIFICATION -> profile.notificationVolume
    VolumeStream.MEDIA -> profile.mediaVolume
    VolumeStream.ALARM -> profile.alarmVolume
    VolumeStream.VOICE_CALL -> profile.voiceCallVolume
    VolumeStream.SYSTEM -> profile.systemVolume
}

fun VolumeStream.copyWith(profile: Profile, value: Int): Profile = when (this) {
    VolumeStream.RINGER -> profile.copy(ringVolume = value)
    VolumeStream.NOTIFICATION -> profile.copy(notificationVolume = value)
    VolumeStream.MEDIA -> profile.copy(mediaVolume = value)
    VolumeStream.ALARM -> profile.copy(alarmVolume = value)
    VolumeStream.VOICE_CALL -> profile.copy(voiceCallVolume = value)
    VolumeStream.SYSTEM -> profile.copy(systemVolume = value)
}
