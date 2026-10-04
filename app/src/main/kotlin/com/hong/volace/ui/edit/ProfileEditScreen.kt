package com.hong.volace.ui.edit

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
                name = "新しいプロファイル",
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
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "一覧に戻る")
                    }
                },
                title = {
                    Text(
                        when {
                            duplicating -> "複製したプロファイル"
                            profileId == null -> "新規プロファイル"
                            else -> "プロファイルを編集"
                        },
                        fontWeight = FontWeight.SemiBold,
                    )
                },
                actions = {
                    if (editId != null) {
                        TextButton(onClick = {
                            scope.launch {
                                profile = current.copy(
                                    id = 0,
                                    name = "${current.name}のコピー",
                                    isActive = false,
                                    orderIndex = dao.nextOrderIndex(),
                                )
                                // Nothing to compare against: backing out asks before dropping it.
                                original = null
                                duplicating = true
                                snackbar.showSnackbar("複製しました。保存すると新しいプロファイルとして追加されます")
                            }
                        }) {
                            Icon(Icons.Filled.ContentCopy, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("複製")
                        }
                        IconButton(onClick = { showDeleteConfirm = true }) {
                            Icon(
                                Icons.Filled.Delete,
                                contentDescription = "削除",
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
                    OutlinedButton(onClick = { leave() }, modifier = Modifier.weight(1f).height(48.dp)) {
                        Text("キャンセル")
                    }
                    Button(
                        enabled = !saving,
                        onClick = onClick@{
                            // Disabling the button only takes effect on the next frame; two taps in
                            // the same frame both reach here, so guard the click itself too.
                            if (saving) return@onClick
                            saving = true
                            scope.launch {
                                val toSave = current.copy(name = current.name.ifBlank { "無題" })
                                val message = if (editId == null) {
                                    dao.insert(toSave)
                                    "「${toSave.name}」を保存しました"
                                } else {
                                    dao.saveEdits(toSave.edits())
                                    // The profile in effect is re-applied, otherwise saving alone
                                    // would turn it into "変更あり" (the device keeps the old values).
                                    val outcome = ProfileSwitcher.apply(context, editId, onlyIfActive = true)
                                    when (outcome?.result) {
                                        null -> "「${toSave.name}」を保存しました"
                                        ApplyResult.Applied -> "「${toSave.name}」を保存して適用しました"
                                        else -> "保存しました。" + outcome.result.message(toSave.name)
                                    }
                                }
                                WidgetRefresher.request(context)
                                onDone(message)
                            }
                        },
                        modifier = Modifier.weight(1f).height(48.dp),
                    ) {
                        Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(if (editingActive) "保存して適用" else "保存", fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        },
    ) { padding ->
        if (showDiscardConfirm) {
            AlertDialog(
                onDismissRequest = { showDiscardConfirm = false },
                title = { Text("変更を破棄しますか？") },
                text = { Text("保存していない変更があります。破棄して一覧に戻りますか？") },
                confirmButton = {
                    TextButton(onClick = {
                        showDiscardConfirm = false
                        onDone(null)
                    }) { Text("破棄して戻る", color = MaterialTheme.colorScheme.error) }
                },
                dismissButton = {
                    TextButton(onClick = { showDiscardConfirm = false }) { Text("編集を続ける") }
                },
            )
        }

        if (showDeleteConfirm) {
            AlertDialog(
                onDismissRequest = { showDeleteConfirm = false },
                title = { Text("プロファイルを削除") },
                text = { Text("「${current.name}」を削除します。元に戻せません。") },
                confirmButton = {
                    TextButton(onClick = {
                        scope.launch {
                            dao.delete(current)
                            WidgetRefresher.request(context)
                            onDone("「${current.name}」を削除しました")
                        }
                    }) { Text("削除", color = MaterialTheme.colorScheme.error) }
                },
                dismissButton = {
                    TextButton(onClick = { showDeleteConfirm = false }) { Text("キャンセル") }
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
                        tint = Color.White,
                        modifier = Modifier.size(28.dp),
                    )
                }
                Spacer(Modifier.width(12.dp))
                OutlinedTextField(
                    value = current.name,
                    onValueChange = { profile = current.copy(name = it) },
                    label = { Text("名前") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
            }

            SectionTitle("着信モード")
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                RingerModeButton(
                    icon = Icons.Filled.VolumeUp,
                    label = "着信音",
                    selected = current.ringerMode == AudioManager.RINGER_MODE_NORMAL,
                    accent = accent,
                    modifier = Modifier.weight(1f),
                ) { profile = ranges.normalize(current.copy(ringerMode = AudioManager.RINGER_MODE_NORMAL)) }
                RingerModeButton(
                    icon = Icons.Filled.Vibration,
                    label = "バイブ",
                    selected = current.ringerMode == AudioManager.RINGER_MODE_VIBRATE,
                    accent = accent,
                    modifier = Modifier.weight(1f),
                ) { profile = ranges.normalize(current.copy(ringerMode = AudioManager.RINGER_MODE_VIBRATE)) }
                RingerModeButton(
                    icon = Icons.Filled.VolumeOff,
                    label = "サイレント",
                    selected = current.ringerMode == AudioManager.RINGER_MODE_SILENT,
                    accent = accent,
                    modifier = Modifier.weight(1f),
                ) { profile = ranges.normalize(current.copy(ringerMode = AudioManager.RINGER_MODE_SILENT)) }
            }
            // "サイレント" reads as "everything silent"; say what still sounds.
            Text(
                text = ringerModeHint(current.ringerMode),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp),
            )

            SectionTitle("音量")
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
                            "現在の着信モードと音量を取り込みました",
                            actionLabel = "元に戻す",
                            duration = SnackbarDuration.Short,
                        )
                        if (result == SnackbarResult.ActionPerformed) profile = before
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Filled.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("現在の着信モードと音量を取り込む")
            }
            Spacer(Modifier.height(4.dp))

            VolumeStream.entries.forEach { stream ->
                StreamSliderRow(
                    stream = stream,
                    value = stream.valueOf(current),
                    range = ranges.of(stream, current.ringerMode),
                    max = ranges.max(stream),
                    accent = accent,
                    note = streamNote(stream, current.ringerMode),
                    kept = stream.isKeptBy(current),
                    onKeptChange = { keep -> profile = stream.keepIn(current, keep) },
                    onValueChange = { newValue -> profile = stream.copyWith(current, newValue) },
                )
            }


            // Looks come last: most visits are to change what the profile sounds like.
            SectionTitle("色")
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
                            .clickable { profile = current.copy(colorArgb = color) },
                        contentAlignment = Alignment.Center,
                    ) {
                        if (selected) {
                            Icon(
                                Icons.Filled.Check,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(20.dp),
                            )
                        }
                    }
                }
            }

            SectionTitle("アイコン")
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
                            .clickable { profile = current.copy(iconKey = entry.key) },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            painter = painterResource(entry.res),
                            contentDescription = entry.label,
                            tint = if (selected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
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
                        stream.label,
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
                    if (kept) "変更しない" else "$value / $max",
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
                    "この音量は変更しない",
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
                ) { Icon(Icons.Filled.Remove, contentDescription = "下げる", modifier = Modifier.size(18.dp)) }
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
                    modifier = Modifier.weight(1f).padding(horizontal = 4.dp),
                )
                IconButton(
                    onClick = { onValueChange((value + 1).coerceAtMost(range.last)) },
                    enabled = !kept && value < range.last,
                    modifier = Modifier.size(36.dp),
                ) { Icon(Icons.Filled.Add, contentDescription = "上げる", modifier = Modifier.size(18.dp)) }
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
        modifier = modifier.height(64.dp).clickable(onClick = onClick),
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = if (selected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp),
            )
            Spacer(Modifier.height(3.dp))
            Text(
                label,
                style = MaterialTheme.typography.labelSmall,
                color = if (selected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
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
        )
    },
)

private fun ringerModeHint(mode: Int): String = when (mode) {
    AudioManager.RINGER_MODE_VIBRATE ->
        "着信・通知は鳴らずにバイブします。メディア・アラーム・通話は下の音量のまま鳴ります。"
    AudioManager.RINGER_MODE_SILENT ->
        "着信・通知は鳴らず、バイブもしません。メディア・アラーム・通話は下の音量のまま鳴ります。" +
            "Android の仕様でサイレント モード（DND）もオンになり、ステータスバーはバイブ表示になります" +
            "（「着信音」に戻すと自動で解除）。"
    else -> "着信・通知は下の音量で鳴ります。"
}

/** Why a slider may not do what it seems to, in this ringer mode. */
private fun streamNote(stream: VolumeStream, ringerMode: Int): String? {
    val ringerSilenced = ringerMode != AudioManager.RINGER_MODE_NORMAL
    return when {
        ringerSilenced && stream in setOf(VolumeStream.RINGER, VolumeStream.NOTIFICATION, VolumeStream.SYSTEM) ->
            "このモードでは鳴りません"
        // Aliased on Pixel (DESIGN.md 8): whichever is written last wins, and that is the ringer.
        stream == VolumeStream.SYSTEM -> "着信音と連動（着信音の値が優先）"
        else -> null
    }
}
