package com.hong.volace.data

import android.media.AudioManager
import com.hong.volace.audio.VolumeApplier
import com.hong.volace.audio.VolumeStream
import kotlin.math.roundToInt

/**
 * Seeded on first launch so the app and its widgets have something meaningful to show before the
 * user has created anything. Values are percentages of each stream's device maximum, because the
 * step count differs per stream (media is 0-25 on a Pixel, ringer only 0-7).
 */
object DefaultProfiles {

    fun build(applier: VolumeApplier): List<Profile> {
        fun v(stream: VolumeStream, percent: Double): Int {
            val max = applier.maxVolume(stream)
            return (max * percent).roundToInt().coerceIn(0, max)
        }

        fun profile(
            name: String,
            order: Int,
            ringerMode: Int,
            ring: Double,
            notification: Double,
            media: Double,
            alarm: Double,
            voice: Double,
            system: Double,
            color: Int,
            icon: ProfileIcon,
        ) = Profile(
            name = name,
            orderIndex = order,
            ringerMode = ringerMode,
            ringVolume = v(VolumeStream.RINGER, ring),
            notificationVolume = v(VolumeStream.NOTIFICATION, notification),
            mediaVolume = v(VolumeStream.MEDIA, media),
            alarmVolume = v(VolumeStream.ALARM, alarm),
            voiceCallVolume = v(VolumeStream.VOICE_CALL, voice),
            systemVolume = v(VolumeStream.SYSTEM, system),
            colorArgb = color,
            iconKey = icon.key,
        )

        return listOf(
            profile(
                "通常", 0, AudioManager.RINGER_MODE_NORMAL,
                ring = 0.70, notification = 0.70, media = 0.60, alarm = 0.80, voice = 0.70, system = 0.70,
                color = ProfilePalette.COLORS[0], icon = ProfileIcon.VOLUME_UP,
            ),
            profile(
                "マナー", 1, AudioManager.RINGER_MODE_VIBRATE,
                ring = 0.0, notification = 0.0, media = 0.45, alarm = 0.80, voice = 0.70, system = 0.0,
                color = ProfilePalette.COLORS[7], icon = ProfileIcon.VIBRATION,
            ),
            profile(
                "サイレント", 2, AudioManager.RINGER_MODE_SILENT,
                ring = 0.0, notification = 0.0, media = 0.30, alarm = 0.60, voice = 0.60, system = 0.0,
                color = ProfilePalette.COLORS[9], icon = ProfileIcon.NIGHT,
            ),
            profile(
                "音楽", 3, AudioManager.RINGER_MODE_NORMAL,
                ring = 0.50, notification = 0.50, media = 1.0, alarm = 0.80, voice = 0.70, system = 0.50,
                color = ProfilePalette.COLORS[1], icon = ProfileIcon.MUSIC,
            ),
        )
    }
}
