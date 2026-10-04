package com.hong.volace.ui.list

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.hong.volace.audio.DeviceVolumes
import com.hong.volace.audio.VolumeApplier
import com.hong.volace.audio.VolumeStream
import com.hong.volace.audio.ringerModeLabel
import com.hong.volace.data.Profile
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import com.hong.volace.R
import androidx.compose.ui.platform.LocalDensity

/** Hidden AudioManager broadcasts; stable for years and the only push signal for volume. */
private const val ACTION_VOLUME_CHANGED = "android.media.VOLUME_CHANGED_ACTION"
private const val ACTION_STREAM_MUTE_CHANGED = "android.media.STREAM_MUTE_CHANGED_ACTION"

/**
 * Holding a volume key fires a burst of broadcasts, and applying a profile writes six streams in
 * a row; reading once things settle also lets the "applied" flag in the database catch up, so the
 * list does not flash "changed" in between.
 */
private const val SETTLE_MS = 150L

/** The device's volumes, kept current while the screen is on (volume keys, system panel...). */
@Composable
fun rememberDeviceVolumes(applier: VolumeApplier): State<DeviceVolumes> {
    val context = LocalContext.current
    val state = remember { mutableStateOf(applier.snapshot()) }
    DisposableEffect(applier) {
        val handler = Handler(Looper.getMainLooper())
        val read = Runnable { state.value = applier.snapshot() }
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                handler.removeCallbacks(read)
                handler.postDelayed(read, SETTLE_MS)
            }
        }
        val filter = IntentFilter().apply {
            addAction(ACTION_VOLUME_CHANGED)
            addAction(ACTION_STREAM_MUTE_CHANGED)
            addAction(AudioManager.RINGER_MODE_CHANGED_ACTION)
        }
        context.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
        read.run()
        onDispose {
            context.unregisterReceiver(receiver)
            handler.removeCallbacks(read)
        }
    }
    return state
}

/**
 * What the device is actually set to, next to which profile was last applied — so a change made
 * with the volume keys (or anything else) is visible instead of the list still claiming the
 * profile is in effect.
 */
@Composable
fun CurrentVolumeCard(
    device: DeviceVolumes,
    maxes: Map<VolumeStream, Int>,
    active: Profile?,
    drifted: Boolean,
) {
    val accent = active?.let { Color(it.colorArgb) } ?: MaterialTheme.colorScheme.primary
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(R.string.current_volume),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
                Icon(
                    imageVector = ringerIcon(device.ringerMode),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(15.dp),
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    stringResource(ringerModeLabel(device.ringerMode)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (active != null) {
                Text(
                    text = stringResource(if (drifted) R.string.card_drifted else R.string.card_active, active.name),
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = if (drifted) FontWeight.SemiBold else FontWeight.Normal,
                    color = if (drifted) accent else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
            Spacer(Modifier.height(6.dp))
            // One column once the text is enlarged; two columns of long names would not fit.
            val columns = if (LocalDensity.current.fontScale >= 1.3f) 1 else 2
            VolumeStream.entries.chunked(columns).forEach { pair ->
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    pair.forEach { stream ->
                        LevelBar(
                            label = stringResource(stream.label),
                            value = device.levelOf(stream),
                            max = maxes[stream] ?: 1,
                            accent = accent,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun LevelBar(label: String, value: Int, max: Int, accent: Color, modifier: Modifier) {
    val safeMax = max.coerceAtLeast(1)
    val fraction = value.coerceIn(0, safeMax).toFloat() / safeMax
    Row(modifier = modifier.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            // Grows with the font size, or enlarged names get cut ("メディ…").
            modifier = Modifier.width(dimensionResource(R.dimen.level_label_width) * LocalDensity.current.fontScale.coerceAtLeast(1f)),
        )
        Box(
            modifier = Modifier
                .weight(1f)
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(MaterialTheme.colorScheme.outlineVariant),
        ) {
            if (value > 0) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(fraction)
                        .clip(RoundedCornerShape(3.dp))
                        .background(accent),
                )
            }
        }
        Text(
            "$value",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.End,
            modifier = Modifier.width(24.dp),
        )
    }
}
