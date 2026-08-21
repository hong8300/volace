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
) {
    RINGER(AudioManager.STREAM_RING, "着信音", "R", Icons.Filled.Notifications),
    NOTIFICATION(AudioManager.STREAM_NOTIFICATION, "通知", "N", Icons.Filled.NotificationsActive),
    MEDIA(AudioManager.STREAM_MUSIC, "メディア", "M", Icons.Filled.MusicNote),
    ALARM(AudioManager.STREAM_ALARM, "アラーム", "A", Icons.Filled.Alarm),
    VOICE_CALL(AudioManager.STREAM_VOICE_CALL, "通話", "V", Icons.Filled.Call),
    SYSTEM(AudioManager.STREAM_SYSTEM, "システム", "S", Icons.Filled.Tune),
}

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
