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
import androidx.compose.material3.Slider
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
import com.hong.volace.audio.message
import com.hong.volace.audio.valueOf
import com.hong.volace.data.Profile
import com.hong.volace.data.ProfileDao
import com.hong.volace.data.ProfileIcon
import com.hong.volace.data.ProfilePalette
import com.hong.volace.data.edits
import com.hong.volace.data.icon
import com.hong.volace.widget.WidgetRefresher
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

    // Back (gesture, key or the arrow) returns to the list. Without this the system back
    // finished the activity, closing the app and silently dropping the edits.
    fun leave() {
        if (profile != original) showDiscardConfirm = true else onDone(null)
    }
    BackHandler { leave() }

    // Read live rather than from the copy loaded on open: a widget may apply another profile
    // while this screen is open.
    val editingActive by remember(profileId) {
        dao.observeAll().map { all -> profileId != null && all.any { it.id == profileId && it.isActive } }
    }.collectAsState(initial = false)

    LaunchedEffect(profileId) {
        if (profile != null) return@LaunchedEffect // restored draft: keep it, don't reload
        val loaded = profileId?.let { dao.getById(it) } ?: run {
            val index = dao.nextOrderIndex()
            Profile(
                name = "新しいプロファイル",
                orderIndex = index,
                ringerMode = AudioManager.RINGER_MODE_NORMAL,
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
        original = loaded
        profile = loaded
    }

    val current = profile
    if (current == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        return
    }
    val accent = Color(current.colorArgb)

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = { leave() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "一覧に戻る")
                    }
                },
                title = {
                    Text(
                        if (profileId == null) "新規プロファイル" else "プロファイルを編集",
                        fontWeight = FontWeight.SemiBold,
                    )
                },
                actions = {
                    if (profileId != null) {
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
                                val message = if (profileId == null) {
                                    dao.insert(toSave)
                                    "「${toSave.name}」を保存しました"
                                } else {
                                    dao.saveEdits(toSave.edits())
                                    // The profile in effect is re-applied, otherwise saving alone
                                    // would turn it into "変更あり" (the device keeps the old values).
                                    val outcome = ProfileSwitcher.apply(context, profileId, onlyIfActive = true)
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
                ) { profile = current.copy(ringerMode = AudioManager.RINGER_MODE_NORMAL) }
                RingerModeButton(
                    icon = Icons.Filled.Vibration,
                    label = "バイブ",
                    selected = current.ringerMode == AudioManager.RINGER_MODE_VIBRATE,
                    accent = accent,
                    modifier = Modifier.weight(1f),
                ) { profile = current.copy(ringerMode = AudioManager.RINGER_MODE_VIBRATE) }
                RingerModeButton(
                    icon = Icons.Filled.VolumeOff,
                    label = "サイレント",
                    selected = current.ringerMode == AudioManager.RINGER_MODE_SILENT,
                    accent = accent,
                    modifier = Modifier.weight(1f),
                ) { profile = current.copy(ringerMode = AudioManager.RINGER_MODE_SILENT) }
            }
            if (current.ringerMode == AudioManager.RINGER_MODE_SILENT) {
                Text(
                    text = "「サイレント」を適用すると、Android の仕様でサイレント モード（DND）も" +
                        "オンになり、ステータスバーはバイブ表示になります。" +
                        "「着信音」に戻すと DND も自動で解除されます。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }

            SectionTitle("音量")
            FilledTonalButton(
                onClick = {
                    var next: Profile = current
                    VolumeStream.entries.forEach { stream ->
                        next = stream.copyWith(next, volumeApplier.currentVolume(stream))
                    }
                    profile = next
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Filled.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("現在の端末の音量を取り込む")
            }
            Spacer(Modifier.height(4.dp))

            VolumeStream.entries.forEach { stream ->
                StreamSliderRow(
                    stream = stream,
                    value = stream.valueOf(current),
                    max = volumeApplier.maxVolume(stream),
                    accent = accent,
                    onValueChange = { newValue -> profile = stream.copyWith(current, newValue) },
                )
            }

            if (VolumeStream.entries.isNotEmpty()) {
                Text(
                    text = "※ この端末では「システム」は「着信音」と連動しています（Android の仕様）。" +
                        "両方を設定した場合は着信音の値が優先されます。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 12.dp),
                )
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
    max: Int,
    accent: Color,
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
                Text(
                    stream.label,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    "$value / $max",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                    onClick = { onValueChange((value - 1).coerceAtLeast(0)) },
                    modifier = Modifier.size(36.dp),
                ) { Icon(Icons.Filled.Remove, contentDescription = "下げる", modifier = Modifier.size(18.dp)) }
                Slider(
                    value = value.toFloat(),
                    onValueChange = { onValueChange(it.toInt()) },
                    valueRange = 0f..max.toFloat(),
                    steps = (max - 1).coerceAtLeast(0),
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
                    onClick = { onValueChange((value + 1).coerceAtMost(max)) },
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
            p.colorArgb, p.iconKey,
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
        )
    },
)
