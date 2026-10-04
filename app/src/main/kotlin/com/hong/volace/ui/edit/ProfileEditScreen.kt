package com.hong.volace.ui.edit

import android.app.Activity
import android.media.RingtoneManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.RingVolume
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.hong.volace.audio.SoundKind
import com.hong.volace.audio.Sounds
import com.hong.volace.audio.setsSounds
import androidx.compose.runtime.produceState
import com.hong.volace.data.VolaceDatabase
import com.hong.volace.schedule.Schedules
import com.hong.volace.ui.schedule.scheduleDeleteNote
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import android.media.AudioManager
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Vibration
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.BottomAppBar
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.hong.volace.audio.ApplyResult
import com.hong.volace.audio.ProfileSwitcher
import com.hong.volace.audio.VolumeApplier
import com.hong.volace.audio.VolumeStream
import com.hong.volace.audio.copyWith
import com.hong.volace.audio.isKeptBy
import com.hong.volace.audio.keepIn
import com.hong.volace.audio.message
import com.hong.volace.audio.ranges
import com.hong.volace.audio.valueOf
import com.hong.volace.data.Profile
import com.hong.volace.data.ProfileDao
import com.hong.volace.data.ProfileIcon
import com.hong.volace.data.ProfilePalette
import com.hong.volace.data.edits
import com.hong.volace.data.icon
import com.hong.volace.widget.WidgetRefresher
import kotlin.math.roundToInt
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import androidx.annotation.StringRes
import androidx.compose.ui.res.stringResource
import com.hong.volace.R
import com.hong.volace.ui.theme.readableOn
import androidx.compose.foundation.selection.selectable
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.foundation.layout.heightIn

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileEditScreen(
    profileId: Long?,
    dao: ProfileDao,
    volumeApplier: VolumeApplier,
    /** Back to the list, with a message to show there (e.g. what was saved), or null. */
    onDone: (message: String?) -> Unit,
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val resources = LocalResources.current
    // Saveable: rotation, a theme switch or the process being reclaimed must not drop the edit.
    var profile by rememberSaveable(stateSaver = ProfileSaver) { mutableStateOf<Profile?>(null) }
    /** As loaded (or as first generated, for a new profile), to tell whether anything changed. */
    var original by rememberSaveable(stateSaver = ProfileSaver) { mutableStateOf<Profile?>(null) }
    var showDeleteConfirm by rememberSaveable { mutableStateOf(false) }
    var showDiscardConfirm by rememberSaveable { mutableStateOf(false) }
    // A second tap while the first save is still writing would insert the new profile twice.
    var saving by remember { mutableStateOf(false) }
    // "複製": the screen turns into a new, unsaved profile prefilled with what is shown.
    var duplicating by rememberSaveable { mutableStateOf(false) }
    val editId = if (duplicating) null else profileId
    val snackbar = remember { SnackbarHostState() }

    // Back (gesture, key or the arrow) returns to the list. Without this the system back
    // finished the activity, closing the app and silently dropping the edits.
    val ranges = remember { volumeApplier.ranges() }

    fun leave() {
        if (profile != original) showDiscardConfirm = true else onDone(null)
    }
    BackHandler { leave() }

    // Read live rather than from the copy loaded on open: a widget may apply another profile
    // while this screen is open.
    val editingActive by remember(editId) {
        dao.observeAll().map { all -> editId != null && all.any { it.id == editId && it.isActive } }
    }.collectAsState(initial = false)

    LaunchedEffect(profileId) {
        if (profile != null) return@LaunchedEffect // restored draft: keep it, don't reload
        val loaded = profileId?.let { dao.getById(it) } ?: run {
            val index = dao.nextOrderIndex()
            Profile(
                name = resources.getString(R.string.new_profile_name),
                orderIndex = index,
                ringerMode = volumeApplier.snapshot().ringerMode,
                ringVolume = volumeApplier.currentVolume(VolumeStream.RINGER),
                notificationVolume = volumeApplier.currentVolume(VolumeStream.NOTIFICATION),
                mediaVolume = volumeApplier.currentVolume(VolumeStream.MEDIA),
                alarmVolume = volumeApplier.currentVolume(VolumeStream.ALARM),
                voiceCallVolume = volumeApplier.currentVolume(VolumeStream.VOICE_CALL),
                systemVolume = volumeApplier.currentVolume(VolumeStream.SYSTEM),
                colorArgb = ProfilePalette.forIndex(index),
                iconKey = ProfileIcon.DEFAULT.key,
            )
        }
        // Normalised before it becomes the baseline, so a stored 0 that the device cannot take
        // does not show as an unsaved change.
        val normalized = ranges.normalize(loaded)
        original = normalized
        profile = normalized
    }

    val current = profile
    if (current == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        return
    }
    val accent = Color(current.colorArgb)

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = { leave() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back_to_list))
                    }
                },
                title = {
                    Text(
                        stringResource(
                            when {
                                duplicating -> R.string.title_duplicate
                                profileId == null -> R.string.title_new
                                else -> R.string.title_edit
                            },
                        ),
                        fontWeight = FontWeight.SemiBold,
                    )
                },
                actions = {
                    if (editId != null) {
                        TextButton(onClick = {
                            scope.launch {
                                profile = current.copy(
                                    id = 0,
                                    name = resources.getString(R.string.copy_name, current.name),
                                    isActive = false,
                                    orderIndex = dao.nextOrderIndex(),
                                )
                                // Nothing to compare against: backing out asks before dropping it.
                                original = null
                                duplicating = true
                                snackbar.showSnackbar(resources.getString(R.string.duplicated_message))
                            }
                        }) {
                            Icon(Icons.Filled.ContentCopy, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(4.dp))
                            Text(stringResource(R.string.duplicate))
                        }
                        IconButton(onClick = { showDeleteConfirm = true }) {
                            Icon(
                                Icons.Filled.Delete,
                                contentDescription = stringResource(R.string.delete),
                                tint = MaterialTheme.colorScheme.error,
                            )
                        }
                    }
                },
            )
        },
        // Spelled-out buttons rather than a bare check mark in the top bar, which read as
        // decoration rather than "save".
        bottomBar = {
            BottomAppBar {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    OutlinedButton(onClick = { leave() }, modifier = Modifier.weight(1f).heightIn(min = 48.dp)) {
                        Text(stringResource(R.string.cancel))
                    }
                    Button(
                        enabled = !saving,
                        onClick = onClick@{
                            // Disabling the button only takes effect on the next frame; two taps in
                            // the same frame both reach here, so guard the click itself too.
                            if (saving) return@onClick
                            saving = true
                            scope.launch {
                                val toSave = current.copy(name = current.name.ifBlank { resources.getString(R.string.untitled) })
                                val message = if (editId == null) {
                                    dao.insert(toSave)
                                    resources.getString(R.string.saved, toSave.name)
                                } else {
                                    dao.saveEdits(toSave.edits())
                                    // The profile in effect is re-applied, otherwise saving alone
                                    // would turn it into "変更あり" (the device keeps the old values).
                                    val outcome = ProfileSwitcher.apply(context, editId, onlyIfActive = true)
                                    when (outcome?.result) {
                                        null -> resources.getString(R.string.saved, toSave.name)
                                        ApplyResult.Applied -> resources.getString(R.string.saved_applied, toSave.name)
                                        else -> resources.getString(
                                            R.string.saved_with_problem,
                                            outcome.result.message(context, toSave.name),
                                        )
                                    }
                                }
                                WidgetRefresher.request(context)
                                onDone(message)
                            }
                        },
                        modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                    ) {
                        Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(
                            stringResource(if (editingActive) R.string.save_and_apply else R.string.save),
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
            }
        },
    ) { padding ->
        if (showDiscardConfirm) {
            AlertDialog(
                onDismissRequest = { showDiscardConfirm = false },
                title = { Text(stringResource(R.string.discard_title)) },
                text = { Text(stringResource(R.string.discard_body)) },
                confirmButton = {
                    TextButton(onClick = {
                        showDiscardConfirm = false
                        onDone(null)
                    }) { Text(stringResource(R.string.discard_confirm), color = MaterialTheme.colorScheme.error) }
                },
                dismissButton = {
                    TextButton(onClick = { showDiscardConfirm = false }) { Text(stringResource(R.string.keep_editing)) }
                },
            )
        }

        if (showDeleteConfirm) {
            val schedule = remember(context) { VolaceDatabase.get(context).scheduleDao() }
            val scheduled by produceState(0, current.id) { value = schedule.countForProfile(current.id) }
            AlertDialog(
                onDismissRequest = { showDeleteConfirm = false },
                title = { Text(stringResource(R.string.delete_title)) },
                text = {
                    Text(
                        listOfNotNull(stringResource(R.string.delete_body, current.name), scheduleDeleteNote(scheduled))
                            .joinToString("\n"),
                    )
                },
                confirmButton = {
                    TextButton(onClick = {
                        scope.launch {
                            dao.delete(current)
                            // Its rules would only fail at their time ("deleted profile").
                            if (scheduled > 0) {
                                schedule.deleteForProfile(current.id)
                                withContext(Dispatchers.IO) { Schedules.onRulesChanged(context) }
                            }
                            WidgetRefresher.request(context)
                            onDone(resources.getString(R.string.deleted, current.name))
                        }
                    }) { Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error) }
                },
                dismissButton = {
                    TextButton(onClick = { showDeleteConfirm = false }) { Text(stringResource(R.string.cancel)) }
                },
            )
        }

        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(bottom = 32.dp),
        ) {
            // ---- identity -------------------------------------------------
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier.size(52.dp).clip(CircleShape).background(accent),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        painter = painterResource(current.icon.res),
                        contentDescription = null,
                        tint = readableOn(accent),
                        modifier = Modifier.size(28.dp),
                    )
                }
                Spacer(Modifier.width(12.dp))
                OutlinedTextField(
                    value = current.name,
                    onValueChange = { profile = current.copy(name = it) },
                    label = { Text(stringResource(R.string.name)) },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
            }

            SectionTitle(stringResource(R.string.ringer_mode))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                RingerModeButton(
                    icon = Icons.Filled.VolumeUp,
                    label = stringResource(R.string.ringer_button_normal),
                    selected = current.ringerMode == AudioManager.RINGER_MODE_NORMAL,
                    accent = accent,
                    modifier = Modifier.weight(1f),
                ) { profile = ranges.normalize(current.copy(ringerMode = AudioManager.RINGER_MODE_NORMAL)) }
                RingerModeButton(
                    icon = Icons.Filled.Vibration,
                    label = stringResource(R.string.ringer_vibrate),
                    selected = current.ringerMode == AudioManager.RINGER_MODE_VIBRATE,
                    accent = accent,
                    modifier = Modifier.weight(1f),
                ) { profile = ranges.normalize(current.copy(ringerMode = AudioManager.RINGER_MODE_VIBRATE)) }
                RingerModeButton(
                    icon = Icons.Filled.VolumeOff,
                    label = stringResource(R.string.ringer_silent),
                    selected = current.ringerMode == AudioManager.RINGER_MODE_SILENT,
                    accent = accent,
                    modifier = Modifier.weight(1f),
                ) { profile = ranges.normalize(current.copy(ringerMode = AudioManager.RINGER_MODE_SILENT)) }
            }
            // "サイレント" reads as "everything silent"; say what still sounds.
            Text(
                text = stringResource(ringerModeHint(current.ringerMode)),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp),
            )

            SectionTitle(stringResource(R.string.section_volume))
            FilledTonalButton(
                onClick = {
                    // The ringer mode too: taking the volumes alone while the phone was on vibrate
                    // gave a "着信音あり" profile with a ringer of 0.
                    val device = volumeApplier.snapshot()
                    var next: Profile = current.copy(ringerMode = device.ringerMode)
                    VolumeStream.entries.forEach { stream ->
                        next = stream.copyWith(next, device.levelOf(stream))
                    }
                    val before = current
                    profile = ranges.normalize(next)
                    scope.launch {
                        snackbar.currentSnackbarData?.dismiss()
                        val result = snackbar.showSnackbar(
                            resources.getString(R.string.captured),
                            actionLabel = resources.getString(R.string.undo),
                            duration = SnackbarDuration.Short,
                        )
                        if (result == SnackbarResult.ActionPerformed) profile = before
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Filled.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.capture))
            }
            Spacer(Modifier.height(4.dp))

            VolumeStream.entries.forEach { stream ->
                StreamSliderRow(
                    stream = stream,
                    value = stream.valueOf(current),
                    range = ranges.of(stream, current.ringerMode),
                    max = ranges.max(stream),
                    accent = accent,
                    note = streamNote(stream, current.ringerMode)?.let { stringResource(it) },
                    kept = stream.isKeptBy(current),
                    onKeptChange = { keep -> profile = stream.keepIn(current, keep) },
                    onValueChange = { newValue -> profile = stream.copyWith(current, newValue) },
                )
            }

            SectionTitle(stringResource(R.string.section_sounds))
            SoundsSection(current, onChange = { profile = it })


            // Looks come last: most visits are to change what the profile sounds like.
            SectionTitle(stringResource(R.string.section_color))
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(vertical = 4.dp),
            ) {
                items(ProfilePalette.COLORS) { color ->
                    val selected = color == current.colorArgb
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(Color(color))
                            .border(
                                width = if (selected) 3.dp else 0.dp,
                                color = if (selected) MaterialTheme.colorScheme.onSurface else Color.Transparent,
                                shape = CircleShape,
                            )
                            // Read as "青, selected" rather than an unlabelled button.
                            .selectable(
                                selected = selected,
                                role = Role.RadioButton,
                                onClick = { profile = current.copy(colorArgb = color) },
                            )
                            .semantics {
                                contentDescription = ProfilePalette.NAMES
                                    .getOrNull(ProfilePalette.COLORS.indexOf(color))
                                    ?.let { resources.getString(it) }
                                    .orEmpty()
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        if (selected) {
                            Icon(
                                Icons.Filled.Check,
                                contentDescription = null,
                                tint = readableOn(Color(color)),
                                modifier = Modifier.size(20.dp),
                            )
                        }
                    }
                }
            }

            SectionTitle(stringResource(R.string.section_icon))
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(vertical = 4.dp),
            ) {
                items(ProfileIcon.entries) { entry ->
                    val selected = entry.key == current.iconKey
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(
                                if (selected) accent else MaterialTheme.colorScheme.surfaceContainerHigh,
                            )
                            .selectable(
                                selected = selected,
                                role = Role.RadioButton,
                                onClick = { profile = current.copy(iconKey = entry.key) },
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            painter = painterResource(entry.res),
                            contentDescription = stringResource(entry.label),
                            tint = if (selected) readableOn(accent) else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(23.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(top = 20.dp, bottom = 6.dp),
    )
}

@Composable
private fun StreamSliderRow(
    stream: VolumeStream,
    value: Int,
    /** What can be chosen; may start above 0 (call, alarm, an audible ringer). */
    range: IntRange,
    max: Int,
    accent: Color,
    /** Shown next to the name, e.g. that the stream does not sound in this ringer mode. */
    note: String?,
    /** "変更しない": applying leaves this stream as it is; the level below is kept for later. */
    kept: Boolean,
    onKeptChange: (Boolean) -> Unit,
    onValueChange: (Int) -> Unit,
) {
    val streamName = stringResource(stream.label)
    val levelDescription = if (kept) stringResource(R.string.keep_value) else stringResource(R.string.a11y_level, value, max)
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
    ) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    stream.icon,
                    contentDescription = null,
                    tint = accent,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(Modifier.width(8.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        stringResource(stream.label),
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium,
                    )
                    if (note != null) {
                        Text(
                            note,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Text(
                    if (kept) stringResource(R.string.keep_value) else "$value / $max",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onKeptChange(!kept) }
                    .padding(vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    stringResource(R.string.keep_switch),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                Switch(
                    checked = kept,
                    onCheckedChange = onKeptChange,
                    modifier = Modifier.scale(0.8f),
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                    onClick = { onValueChange((value - 1).coerceAtLeast(range.first)) },
                    enabled = !kept && value > range.first,
                    modifier = Modifier.size(36.dp),
                ) {
                    Icon(
                        Icons.Filled.Remove,
                        contentDescription = stringResource(R.string.a11y_lower, stringResource(stream.label)),
                        modifier = Modifier.size(18.dp),
                    )
                }
                Slider(
                    value = value.toFloat(),
                    enabled = !kept,
                    // Rounded: the snapped float can land a hair under the step (2.9999998).
                    onValueChange = { onValueChange(it.roundToInt()) },
                    valueRange = range.first.toFloat()..range.last.toFloat(),
                    steps = (range.last - range.first - 1).coerceAtLeast(0),
                    // Tick marks are noise at 25 steps; the numeric badge above is the precise read-out.
                    colors = SliderDefaults.colors(
                        thumbColor = accent,
                        activeTrackColor = accent,
                        activeTickColor = Color.Transparent,
                        inactiveTickColor = Color.Transparent,
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 4.dp)
                        .semantics {
                            contentDescription = streamName
                            stateDescription = levelDescription
                        },
                )
                IconButton(
                    onClick = { onValueChange((value + 1).coerceAtMost(range.last)) },
                    enabled = !kept && value < range.last,
                    modifier = Modifier.size(36.dp),
                ) {
                    Icon(
                        Icons.Filled.Add,
                        contentDescription = stringResource(R.string.a11y_raise, stringResource(stream.label)),
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun RingerModeButton(
    icon: ImageVector,
    label: String,
    selected: Boolean,
    accent: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = if (selected) accent else MaterialTheme.colorScheme.surfaceContainerHigh,
        // heightIn: a fixed 64dp clipped the label at large font sizes.
        modifier = modifier
            .heightIn(min = 64.dp)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = if (selected) readableOn(accent) else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp),
            )
            Spacer(Modifier.height(3.dp))
            Text(
                label,
                style = MaterialTheme.typography.labelSmall,
                color = if (selected) readableOn(accent) else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** Puts an unsaved [Profile] into saved instance state (it is not Parcelable). */
private val ProfileSaver = listSaver<Profile?, Any>(
    save = { p ->
        if (p == null) emptyList() else listOf(
            p.id, p.name, p.orderIndex, p.ringerMode, p.ringVolume, p.notificationVolume,
            p.mediaVolume, p.alarmVolume, p.voiceCallVolume, p.systemVolume, p.isActive,
            p.colorArgb, p.iconKey, p.keepMask,
            p.ringtoneUri ?: NO_SOUND, p.notificationSoundUri ?: NO_SOUND, p.alarmSoundUri ?: NO_SOUND,
        )
    },
    restore = { v ->
        if (v.isEmpty()) null else Profile(
            id = v[0] as Long,
            name = v[1] as String,
            orderIndex = v[2] as Int,
            ringerMode = v[3] as Int,
            ringVolume = v[4] as Int,
            notificationVolume = v[5] as Int,
            mediaVolume = v[6] as Int,
            alarmVolume = v[7] as Int,
            voiceCallVolume = v[8] as Int,
            systemVolume = v[9] as Int,
            isActive = v[10] as Boolean,
            colorArgb = v[11] as Int,
            iconKey = v[12] as String,
            keepMask = v[13] as Int,
            ringtoneUri = (v[14] as String).takeIf { it != NO_SOUND },
            notificationSoundUri = (v[15] as String).takeIf { it != NO_SOUND },
            alarmSoundUri = (v[16] as String).takeIf { it != NO_SOUND },
        )
    },
)

/** A null sound ("変更しない") in [ProfileSaver], which cannot hold nulls; "" is taken by "なし". */
private const val NO_SOUND = "\u0000"

/**
 * "音": the default ringtone, notification and alarm sounds the profile sets, each "変更しない"
 * unless chosen. Writing them needs "システム設定の変更", asked for here once a sound is chosen.
 */
@Composable
private fun SoundsSection(profile: Profile, onChange: (Profile) -> Unit) {
    val context = LocalContext.current
    var canWrite by remember { mutableStateOf(Sounds.canWrite(context)) }
    // Back from Settings with the switch turned on (or off).
    LifecycleResumeEffect(Unit) {
        canWrite = Sounds.canWrite(context)
        onPauseOrDispose {}
    }
    var picking by rememberSaveable { mutableStateOf<SoundKind?>(null) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val kind = picking ?: return@rememberLauncherForActivityResult
        picking = null
        if (result.resultCode != Activity.RESULT_OK) return@rememberLauncherForActivityResult
        // No URI back is the picker's "None".
        val uri = result.data?.getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI, Uri::class.java)
        onChange(kind.copyWith(profile, uri?.toString() ?: SoundKind.SILENT))
    }

    SoundKind.entries.forEach { kind ->
        val value = kind.valueOf(profile)
        val label = stringResource(kind.label)
        val unknown = stringResource(R.string.sound_unknown)
        val title by produceState<String?>(null, value) {
            this.value = value?.takeIf { it != SoundKind.SILENT }?.let {
                withContext(Dispatchers.IO) { Sounds.title(context, it) } ?: unknown
            }
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 56.dp)
                .clip(RoundedCornerShape(12.dp))
                .clickable {
                    picking = kind
                    picker.launch(Sounds.pickerIntent(context, kind, value))
                }
                .padding(horizontal = 4.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                when (kind) {
                    SoundKind.RINGTONE -> Icons.Filled.RingVolume
                    SoundKind.NOTIFICATION -> Icons.Filled.NotificationsActive
                    SoundKind.ALARM -> Icons.Filled.Alarm
                },
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(22.dp),
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(label, style = MaterialTheme.typography.bodyLarge)
                Text(
                    when (value) {
                        null -> stringResource(R.string.sound_keep)
                        SoundKind.SILENT -> stringResource(R.string.sound_none)
                        else -> title ?: ""
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = if (value == null) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.primary,
                )
            }
            if (value != null) {
                IconButton(onClick = { onChange(kind.copyWith(profile, null)) }) {
                    Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.sound_reset, label))
                }
            }
        }
    }
    if (profile.setsSounds && !canWrite) {
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.errorContainer,
            modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
        ) {
            Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                Text(
                    stringResource(R.string.sound_permission),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                )
                TextButton(onClick = { context.startActivity(Sounds.writeSettingsIntent(context)) }) {
                    Text(stringResource(R.string.sound_permission_button))
                }
            }
        }
    }
    Text(
        stringResource(R.string.sound_note),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 6.dp),
    )
}

@StringRes
private fun ringerModeHint(mode: Int): Int = when (mode) {
    AudioManager.RINGER_MODE_VIBRATE -> R.string.hint_vibrate
    AudioManager.RINGER_MODE_SILENT -> R.string.hint_silent
    else -> R.string.hint_normal
}

/** Why a slider may not do what it seems to, in this ringer mode. */
@StringRes
private fun streamNote(stream: VolumeStream, ringerMode: Int): Int? {
    val ringerSilenced = ringerMode != AudioManager.RINGER_MODE_NORMAL
    return when {
        ringerSilenced && stream in setOf(VolumeStream.RINGER, VolumeStream.NOTIFICATION, VolumeStream.SYSTEM) ->
            R.string.note_silenced
        // Aliased on Pixel (DESIGN.md 8): whichever is written last wins, and that is the ringer.
        stream == VolumeStream.SYSTEM -> R.string.note_system_linked
        else -> null
    }
}
