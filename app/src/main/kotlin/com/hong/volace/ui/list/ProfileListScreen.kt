package com.hong.volace.ui.list

import android.content.Intent
import android.media.AudioManager
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material.icons.filled.Widgets
import androidx.compose.material.icons.filled.Vibration
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.BottomAppBar
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hong.volace.audio.ProfileSwitcher
import com.hong.volace.audio.VolumeApplier
import com.hong.volace.audio.message
import com.hong.volace.audio.VolumeStream
import com.hong.volace.audio.ringerModeLabel
import com.hong.volace.audio.valueOf
import com.hong.volace.data.Profile
import com.hong.volace.data.ProfileDao
import com.hong.volace.data.icon
import com.hong.volace.tile.ProfileTileService
import com.hong.volace.widget.WidgetRefresher
import com.hong.volace.widget.WidgetStyle
import com.hong.volace.widget.requestPinWidget
import kotlinx.coroutines.launch

private val BarHeight = 30.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileListScreen(
    dao: ProfileDao,
    volumeApplier: VolumeApplier,
    onEditProfile: (Long) -> Unit,
    onAddProfile: () -> Unit,
    /** Left by the edit screen ("保存しました" etc.); shown once, then [onMessageShown]. */
    message: String? = null,
    onMessageShown: () -> Unit = {},
) {
    // null until the first query returns, so "no profiles" is not flashed while loading. The flow
    // is created once: calling observeAll() on every recomposition restarted the query each time.
    val loaded by remember(dao) { dao.observeAll() }.collectAsState(initial = null)
    val profiles = loaded.orEmpty()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var reorderMode by remember { mutableStateOf(false) }
    var showWidgetPicker by remember { mutableStateOf(false) }
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(message) {
        if (message == null) return@LaunchedEffect
        snackbar.currentSnackbarData?.dismiss()
        snackbar.showSnackbar(message)
        onMessageShown()
    }
    val maxes = remember { VolumeStream.entries.associateWith { volumeApplier.maxVolume(it) } }
    val device by rememberDeviceVolumes(volumeApplier)
    val active = profiles.firstOrNull { it.isActive }
    val drifted = active != null && !volumeApplier.matches(active, device)

    if (showWidgetPicker) {
        AddWidgetDialog(onDismiss = { showWidgetPicker = false })
    }

    // Every action is spelled out: bare icons (a widget grid, a check mark) were not recognised.
    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text("Volace", fontWeight = FontWeight.Bold) },
                actions = {
                    if (profiles.size > 1) {
                        TextButton(onClick = { reorderMode = !reorderMode }) {
                            Icon(
                                if (reorderMode) Icons.Filled.Check else Icons.Filled.SwapVert,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(if (reorderMode) "完了" else "並べ替え")
                        }
                    }
                },
            )
        },
        bottomBar = {
            BottomAppBar {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    // The app is mostly opened from a widget; this is the way back to it.
                    BarAction(Icons.Filled.Home, "ホーム画面へ", Modifier.weight(1f)) {
                        context.startActivity(
                            Intent(Intent.ACTION_MAIN)
                                .addCategory(Intent.CATEGORY_HOME)
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                        )
                    }
                    BarAction(Icons.Filled.Widgets, "ウィジェット追加", Modifier.weight(1f)) {
                        showWidgetPicker = true
                    }
                    BarAction(
                        Icons.Filled.Add,
                        "プロファイル追加",
                        Modifier.weight(1f),
                        emphasized = true,
                        onClick = onAddProfile,
                    )
                }
            }
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.padding(padding).fillMaxSize(),
            contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 4.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item(key = "current-volume") {
                CurrentVolumeCard(device = device, maxes = maxes, active = active, drifted = drifted)
            }
            item {
                Text(
                    text = if (reorderMode) "↑↓ で並び順を変更（ウィジェットの表示順にもなります）"
                    else "タップで即時適用・鉛筆アイコンで編集",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 6.dp),
                )
            }
            items(profiles, key = { it.id }) { profile ->
                ProfileRow(
                    profile = profile,
                    drifted = profile.isActive && drifted,
                    maxes = maxes,
                    reorderMode = reorderMode,
                    onApply = {
                        scope.launch {
                            val outcome = ProfileSwitcher.apply(context, profile.id) ?: return@launch
                            WidgetRefresher.request(context)
                            snackbar.currentSnackbarData?.dismiss()
                            snackbar.showSnackbar(outcome.result.message(outcome.profile.name))
                        }
                    },
                    onEdit = { onEditProfile(profile.id) },
                    onMove = { delta ->
                        scope.launch {
                            dao.move(profile.id, delta)
                            WidgetRefresher.request(context)
                        }
                    },
                )
            }
            if (loaded?.isEmpty() == true) {
                item {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(
                            "プロファイルがありません。",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(12.dp))
                        // A new profile starts from the device's current volumes.
                        Button(onClick = onAddProfile) {
                            Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("今の音量でプロファイルを作成")
                        }
                    }
                }
            }
        }
    }
}

/** An icon with its name underneath, for the bottom bar. [emphasized] marks the main action. */
@Composable
private fun BarAction(
    icon: ImageVector,
    label: String,
    modifier: Modifier = Modifier,
    emphasized: Boolean = false,
    onClick: () -> Unit,
) {
    val content = if (emphasized) MaterialTheme.colorScheme.onPrimaryContainer
    else MaterialTheme.colorScheme.onSurfaceVariant
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(if (emphasized) MaterialTheme.colorScheme.primaryContainer else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(icon, contentDescription = null, tint = content, modifier = Modifier.size(22.dp))
        Spacer(Modifier.height(3.dp))
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = if (emphasized) FontWeight.SemiBold else FontWeight.Normal,
            color = content,
            maxLines = 1,
        )
    }
}

@Composable
private fun AddWidgetDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    var failed by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("ホーム画面・クイック設定に追加") },
        text = {
            Column {
                WidgetStyle.entries.forEach { style ->
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                            .clickable {
                                if (requestPinWidget(context, style)) onDismiss() else failed = true
                            },
                    ) {
                        Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                            Text(style.title, fontWeight = FontWeight.SemiBold)
                            Text(
                                style.subtitle,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
                // Not a widget, but the same question ("where else can I switch from?").
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                        .clickable {
                            ProfileTileService.requestAdd(context)
                            onDismiss()
                        },
                ) {
                    Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                        Text("クイック設定タイル", fontWeight = FontWeight.SemiBold)
                        Text(
                            "通知シェードを下ろして、どの画面からでも切り替え",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                if (failed) {
                    Text(
                        "このランチャーは自動追加に対応していません。ホーム画面の空きを長押し →" +
                            "「ウィジェット」から Volace を探してください。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("閉じる") } },
    )
}

@Composable
private fun ProfileRow(
    profile: Profile,
    /** Applied, but the device has been changed since (volume keys, another app...). */
    drifted: Boolean,
    maxes: Map<VolumeStream, Int>,
    reorderMode: Boolean,
    onApply: () -> Unit,
    onEdit: () -> Unit,
    onMove: (Int) -> Unit,
) {
    val accent = Color(profile.colorArgb)
    // A drifted profile keeps only an outline, like its widget cell: tapping it re-applies.
    val active = profile.isActive && !drifted

    Surface(
        shape = RoundedCornerShape(20.dp),
        color = if (active) accent.copy(alpha = 0.16f) else MaterialTheme.colorScheme.surfaceContainer,
        border = when {
            active -> BorderStroke(2.dp, accent)
            drifted -> BorderStroke(1.5.dp, accent.copy(alpha = 0.7f))
            else -> null
        },
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(enabled = !reorderMode, onClick = onApply)
                .padding(horizontal = 12.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .clip(CircleShape)
                    .background(if (active) accent else accent.copy(alpha = 0.20f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = painterResource(profile.icon.res),
                    contentDescription = null,
                    tint = if (active) Color.White else accent,
                    modifier = Modifier.size(24.dp),
                )
            }

            Spacer(Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = profile.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                    )
                    if (profile.isActive) {
                        Spacer(Modifier.width(8.dp))
                        ActivePill(accent, drifted)
                    }
                }
                Spacer(Modifier.height(3.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = ringerIcon(profile.ringerMode),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(14.dp),
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = ringerModeLabel(profile.ringerMode),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            if (reorderMode) {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    FilledIconButton(
                        onClick = { onMove(-1) },
                        modifier = Modifier.size(38.dp),
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                            contentColor = MaterialTheme.colorScheme.onSurface,
                        ),
                    ) { Icon(Icons.Filled.KeyboardArrowUp, contentDescription = "上へ") }
                    FilledIconButton(
                        onClick = { onMove(1) },
                        modifier = Modifier.size(38.dp),
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                            contentColor = MaterialTheme.colorScheme.onSurface,
                        ),
                    ) { Icon(Icons.Filled.KeyboardArrowDown, contentDescription = "下へ") }
                }
            } else {
                MiniVolumeBars(profile, maxes, accent)
                Spacer(Modifier.width(4.dp))
                IconButton(onClick = onEdit, modifier = Modifier.size(44.dp)) {
                    Icon(
                        Icons.Filled.Edit,
                        contentDescription = "編集",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun ActivePill(accent: Color, drifted: Boolean) {
    if (drifted) {
        Text(
            "変更あり",
            color = accent,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier
                .clip(RoundedCornerShape(50))
                .border(1.dp, accent, RoundedCornerShape(50))
                .padding(horizontal = 7.dp, vertical = 1.dp),
        )
        return
    }
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(accent)
            .padding(horizontal = 7.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            Icons.Filled.Check,
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.size(11.dp),
        )
        Spacer(Modifier.width(2.dp))
        Text("適用中", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
    }
}

/**
 * Six bars, one per stream, each scaled against **that stream's own** maximum. Normalising
 * against the largest value in the profile (the previous behaviour) made e.g. media 12/25 look
 * taller than ringer 7/7.
 */
@Composable
private fun MiniVolumeBars(profile: Profile, maxes: Map<VolumeStream, Int>, accent: Color) {
    val trackColor = MaterialTheme.colorScheme.outlineVariant
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.Bottom) {
        VolumeStream.entries.forEach { stream ->
            val max = (maxes[stream] ?: 1).coerceAtLeast(1)
            val value = stream.valueOf(profile).coerceIn(0, max)
            val fraction = value.toFloat() / max
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    modifier = Modifier
                        .width(8.dp)
                        .height(BarHeight)
                        .clip(RoundedCornerShape(4.dp))
                        .background(trackColor),
                    contentAlignment = Alignment.BottomCenter,
                ) {
                    if (value > 0) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(BarHeight * fraction)
                                .clip(RoundedCornerShape(4.dp))
                                .background(accent),
                        )
                    }
                }
                Text(
                    text = stream.shortLabel,
                    fontSize = 8.sp,
                    color = labelColor,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }
    }
}

internal fun ringerIcon(mode: Int) = when (mode) {
    AudioManager.RINGER_MODE_SILENT -> Icons.Filled.VolumeOff
    AudioManager.RINGER_MODE_VIBRATE -> Icons.Filled.Vibration
    else -> Icons.Filled.VolumeUp
}
