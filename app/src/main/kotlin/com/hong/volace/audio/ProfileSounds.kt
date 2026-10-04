package com.hong.volace.audio

import android.content.Context
import android.content.Intent
import android.media.RingtoneManager
import android.net.Uri
import android.provider.Settings
import androidx.annotation.StringRes
import androidx.core.net.toUri
import com.hong.volace.R
import com.hong.volace.data.Profile

/**
 * The device's default sounds a profile may set (DESIGN.md 5.16). A profile holds, per kind, null
 * ("変更しない"), [SILENT] ("なし") or a sound's URI.
 *
 * Only the defaults: sounds another app sets for its own notifications, and the sound of each
 * alarm in a clock app, are theirs and do not change.
 */
enum class SoundKind(
    val ringtoneType: Int,
    @StringRes val label: Int,
    /** Name in a timer's saved state. Never rename. */
    val key: String,
) {
    RINGTONE(RingtoneManager.TYPE_RINGTONE, R.string.sound_ringtone, "ringtone"),
    NOTIFICATION(RingtoneManager.TYPE_NOTIFICATION, R.string.sound_notification, "notification"),
    ALARM(RingtoneManager.TYPE_ALARM, R.string.sound_alarm, "alarm"),
    ;

    companion object {
        /** "なし": the picker's "None", stored apart from null ("変更しない"). */
        const val SILENT = ""
    }
}

fun SoundKind.valueOf(profile: Profile): String? = when (this) {
    SoundKind.RINGTONE -> profile.ringtoneUri
    SoundKind.NOTIFICATION -> profile.notificationSoundUri
    SoundKind.ALARM -> profile.alarmSoundUri
}

fun SoundKind.copyWith(profile: Profile, value: String?): Profile = when (this) {
    SoundKind.RINGTONE -> profile.copy(ringtoneUri = value)
    SoundKind.NOTIFICATION -> profile.copy(notificationSoundUri = value)
    SoundKind.ALARM -> profile.copy(alarmSoundUri = value)
}

/** Whether applying [this] profile writes any sound (and so needs [Sounds.canWrite]). */
val Profile.setsSounds: Boolean get() = SoundKind.entries.any { it.valueOf(this) != null }

object Sounds {

    /** "システム設定の変更" (WRITE_SETTINGS): a special access the user grants in Settings. */
    fun canWrite(context: Context): Boolean = Settings.System.canWrite(context)

    /** Opens Settings at Volace's "システム設定の変更" switch. */
    fun writeSettingsIntent(context: Context): Intent =
        Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS, "package:${context.packageName}".toUri())

    /** The device's default for [kind] now, as a profile would store it. */
    fun current(context: Context, kind: SoundKind): String =
        RingtoneManager.getActualDefaultRingtoneUri(context, kind.ringtoneType)?.toString() ?: SoundKind.SILENT

    /** Throws when Android refuses (no access, or a sound it may not read). */
    fun set(context: Context, kind: SoundKind, value: String) {
        val uri: Uri? = value.takeIf { it != SoundKind.SILENT }?.toUri()
        RingtoneManager.setActualDefaultRingtoneUri(context, kind.ringtoneType, uri)
    }

    /** The sound's name as the picker shows it; null when it cannot be read (e.g. deleted). */
    fun title(context: Context, value: String): String? = runCatching {
        RingtoneManager.getRingtone(context, value.toUri())?.getTitle(context)
    }.getOrNull()

    /** The system's sound picker for [kind], starting at [value]. */
    fun pickerIntent(context: Context, kind: SoundKind, value: String?): Intent =
        Intent(RingtoneManager.ACTION_RINGTONE_PICKER)
            .putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, kind.ringtoneType)
            .putExtra(RingtoneManager.EXTRA_RINGTONE_TITLE, context.getString(kind.label))
            .putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, true)
            // "Default" would point at the very setting the profile writes.
            .putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, false)
            .putExtra(
                RingtoneManager.EXTRA_RINGTONE_EXISTING_URI,
                (value ?: current(context, kind)).takeIf { it != SoundKind.SILENT }?.toUri(),
            )
}
