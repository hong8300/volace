package com.hong.volace.ui.timer

import android.Manifest
import android.app.TimePickerDialog
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.text.format.DateFormat
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.hong.volace.R
import com.hong.volace.audio.ProfileSwitcher
import com.hong.volace.data.Profile
import com.hong.volace.timer.ProfileTimers
import com.hong.volace.timer.nextOccurrence
import com.hong.volace.timer.timerEndText
import kotlinx.coroutines.launch
import java.util.Calendar

/** What the dialog asks [ProfileTimers.start] for. */
data class TimerRequest(val profileId: Long, val endAt: Long, val restoreId: Long?)

private const val MINUTE_MS = 60_000L
private val DURATIONS_MIN = listOf(30, 60, 120, 180)

/**
 * "時間指定": how long to apply [profile] for, and what to switch to afterwards (the state from
 * before, or another profile).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TimedApplyDialog(
    profile: Profile,
    profiles: List<Profile>,
    onConfirm: (TimerRequest) -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    // Minutes from now, or -1 for "until a time" with untilHour / untilMinute.
    var minutes by rememberSaveable { mutableIntStateOf(60) }
    var untilHour by rememberSaveable { mutableIntStateOf(-1) }
    var untilMinute by rememberSaveable { mutableIntStateOf(0) }
    // Null: back to the state from before.
    var restoreId by rememberSaveable { mutableStateOf<Long?>(null) }
    // A one-minute choice in debug builds, to try the end without waiting half an hour.
    val debuggable = remember { context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0 }
    val durations = if (debuggable) listOf(1) + DURATIONS_MIN else DURATIONS_MIN

    fun endAt(now: Long = System.currentTimeMillis()): Long =
        if (minutes > 0) now + minutes * MINUTE_MS else nextOccurrence(now, untilHour, untilMinute)

    fun pickTime() {
        val start = Calendar.getInstance().apply { timeInMillis = endAt() }
        TimePickerDialog(
            context,
            { _, hour, minute ->
                untilHour = hour
                untilMinute = minute
                minutes = -1
            },
            start.get(Calendar.HOUR_OF_DAY),
            start.get(Calendar.MINUTE),
            DateFormat.is24HourFormat(context),
        ).show()
    }

    val others = profiles.filter { it.id != profile.id }
    val restoreName = others.firstOrNull { it.id == restoreId }?.name ?: stringResource(R.string.timer_previous_state)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.timer_dialog_title, profile.name)) },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                Text(stringResource(R.string.timer_how_long), style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.height(6.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    durations.forEach { option ->
                        FilterChip(
                            selected = minutes == option,
                            onClick = { minutes = option },
                            label = { Text(durationLabel(option)) },
                        )
                    }
                    FilterChip(
                        selected = minutes < 0,
                        onClick = ::pickTime,
                        label = {
                            Text(
                                if (minutes < 0) stringResource(R.string.timer_until, timerEndText(context, endAt()))
                                else stringResource(R.string.timer_until_time),
                            )
                        },
                    )
                }
                Spacer(Modifier.height(14.dp))
                Text(stringResource(R.string.timer_then), style = MaterialTheme.typography.titleSmall)
                Column(modifier = Modifier.selectableGroup()) {
                    RestoreOption(stringResource(R.string.timer_restore_previous), restoreId == null) { restoreId = null }
                    others.forEach { other ->
                        RestoreOption(stringResource(R.string.timer_restore_profile, other.name), restoreId == other.id) {
                            restoreId = other.id
                        }
                    }
                }
                Spacer(Modifier.height(10.dp))
                Text(
                    stringResource(R.string.timer_summary, timerEndText(context, endAt()), restoreName),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(TimerRequest(profile.id, endAt(), restoreId)) }) {
                Text(stringResource(R.string.apply))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

@Composable
private fun durationLabel(minutes: Int): String =
    if (minutes % 60 == 0) pluralStringResource(R.plurals.timer_hours, minutes / 60, minutes / 60)
    else pluralStringResource(R.plurals.timer_minutes, minutes, minutes)

@Composable
private fun RestoreOption(label: String, selected: Boolean, onSelect: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .selectable(selected = selected, onClick = onSelect, role = Role.RadioButton)
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = null)
        Spacer(Modifier.width(8.dp))
        Text(label, style = MaterialTheme.typography.bodyMedium)
    }
}

/**
 * Starts a timer from a screen: asks for the notification permission first (the running timer
 * shows as a notification; the timer works without it too), then calls [ProfileTimers.start]
 * while the screen is still visible, which the end needs on Android 17 (DESIGN.md 5.14).
 */
@Composable
fun rememberTimerStarter(onDone: suspend (ProfileSwitcher.Outcome?, TimerRequest) -> Unit): (TimerRequest) -> Unit {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var pending by remember { mutableStateOf<TimerRequest?>(null) }
    fun start(request: TimerRequest) {
        scope.launch {
            onDone(ProfileTimers.start(context, request.profileId, request.endAt, request.restoreId), request)
        }
    }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        pending?.let(::start)
        pending = null
    }
    return { request ->
        if (context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) {
            start(request)
        } else {
            pending = request
            permission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
}
