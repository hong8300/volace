package com.hong.volace.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.hong.volace.R
import com.hong.volace.audio.VolumeApplier
import com.hong.volace.data.ProfileDao
import com.hong.volace.ui.backup.BackupDialog
import com.hong.volace.ui.theme.Skin
import com.hong.volace.ui.theme.SkinStore
import com.hong.volace.widget.WidgetPalette
import com.hong.volace.widget.WidgetRefresher

/** "設定": the skin, and the backup. Each opens its own dialog. */
@Composable
fun SettingsDialog(
    dao: ProfileDao,
    volumeApplier: VolumeApplier,
    onDismiss: () -> Unit,
    onMessage: (String) -> Unit,
) {
    val context = LocalContext.current
    val skin by SkinStore.state(context).collectAsState()
    var page by remember { mutableStateOf(Page.MENU) }

    when (page) {
        Page.SKIN -> SkinDialog(current = skin, onDismiss = onDismiss)
        Page.BACKUP -> BackupDialog(dao, volumeApplier, onDismiss = onDismiss, onMessage = onMessage)
        Page.MENU -> AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text(stringResource(R.string.settings)) },
            text = {
                Column {
                    Entry(
                        stringResource(R.string.settings_skin),
                        stringResource(R.string.settings_skin_desc, stringResource(skin.label)),
                    ) { page = Page.SKIN }
                    Entry(stringResource(R.string.backup), stringResource(R.string.settings_backup_desc)) {
                        page = Page.BACKUP
                    }
                }
            },
            confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.close)) } },
        )
    }
}

private enum class Page { MENU, SKIN, BACKUP }

@Composable
private fun SkinDialog(current: Skin, onDismiss: () -> Unit) {
    val context = LocalContext.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.settings_skin)) },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                Skin.entries.forEach { skin ->
                    val selected = skin == current
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                SkinStore.set(context, skin)
                                // The widgets take their colours from the skin too.
                                WidgetRefresher.request(context)
                            }
                            .padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = selected, onClick = null)
                        Spacer(Modifier.width(8.dp))
                        Swatch(skin)
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(stringResource(skin.label), fontWeight = FontWeight.SemiBold)
                            Text(
                                stringResource(skin.description),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.close)) } },
    )
}

/** A small widget-like chip in the skin's own colours (light half / dark half for AUTO, DYNAMIC). */
@Composable
private fun Swatch(skin: Skin) {
    val context = LocalContext.current
    val palette = remember(skin) { WidgetPalette.of(context, skin) }
    Row(
        modifier = Modifier
            .size(width = 44.dp, height = 30.dp)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(8.dp))
            .padding(1.dp),
    ) {
        listOf(palette.day, palette.night).distinct().forEach { colors ->
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .background(Color(colors.background), RoundedCornerShape(7.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Box(Modifier.size(10.dp).background(Color(colors.accent), CircleShape))
            }
        }
    }
}

@Composable
private fun Entry(title: String, subtitle: String, onClick: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).clickable(onClick = onClick),
    ) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
            Text(title, fontWeight = FontWeight.SemiBold)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
