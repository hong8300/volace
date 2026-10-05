package com.hong.volace.ui.settings

import androidx.compose.foundation.selection.selectable
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.FilterChip
import com.hong.volace.data.ProfileIcon
import com.hong.volace.ui.theme.IconStyle
import com.hong.volace.ui.theme.ProfileIconView
import com.hong.volace.ui.theme.SkinGroup
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
import com.hong.volace.alarm.ExactAlarms
import com.hong.volace.ui.alarm.rememberExactAlarmAllowed

/** "設定": the skin, the backup, the alarm permission and the privacy policy / licenses (Play wants the policy in the app). */
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
    val exactAlarmAllowed = rememberExactAlarmAllowed()

    when (page) {
        Page.SKIN -> SkinDialog(current = skin, onDismiss = onDismiss)
        Page.BACKUP -> BackupDialog(dao, volumeApplier, onDismiss = onDismiss, onMessage = onMessage)
        Page.MENU -> AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text(stringResource(R.string.settings)) },
            text = {
                // Scrolls: at large font sizes the entries do not fit on one screen.
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    Entry(
                        stringResource(R.string.settings_skin),
                        stringResource(R.string.settings_skin_desc, stringResource(skin.label)),
                    ) { page = Page.SKIN }
                    Entry(stringResource(R.string.backup), stringResource(R.string.settings_backup_desc)) {
                        page = Page.BACKUP
                    }
                    Entry(
                        stringResource(R.string.settings_exact_alarm),
                        stringResource(if (exactAlarmAllowed) R.string.settings_exact_alarm_on else R.string.settings_exact_alarm_off),
                    ) { context.startActivity(ExactAlarms.settingsIntent(context)) }
                    Entry(stringResource(R.string.settings_privacy), stringResource(R.string.settings_link_desc)) {
                        openLink(context, Links.PRIVACY)
                    }
                    Entry(stringResource(R.string.settings_licenses), stringResource(R.string.settings_link_desc)) {
                        openLink(context, Links.NOTICES)
                    }
                }
            },
            confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.close)) } },
        )
    }
}

private enum class Page { MENU, SKIN, BACKUP }

/**
 * "スキン": the icon style (line icons or emoji) on top, then the skins by group. Both apply at
 * once, to the app and the widgets.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SkinDialog(current: Skin, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val iconStyle by SkinStore.iconState(context).collectAsState()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.settings_skin)) },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                Text(stringResource(R.string.icon_style_title), style = MaterialTheme.typography.titleSmall)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.selectableGroup()) {
                    IconStyle.entries.forEach { style ->
                        FilterChip(
                            selected = style == iconStyle,
                            onClick = {
                                SkinStore.setIconStyle(context, style)
                                // The widgets and launcher shortcuts draw the icons too.
                                WidgetRefresher.request(context)
                            },
                            label = { Text(stringResource(style.label)) },
                        )
                    }
                }
                // What the style looks like, on a few of the icons.
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    listOf(ProfileIcon.BELL, ProfileIcon.MUSIC, ProfileIcon.NIGHT, ProfileIcon.CAT, ProfileIcon.FLOWER).forEach {
                        ProfileIconView(it, tint = MaterialTheme.colorScheme.primary, size = 22.dp)
                    }
                }
                Text(
                    stringResource(R.string.icon_style_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )
                SkinGroup.entries.forEach { group ->
                    Text(
                        stringResource(group.label),
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.padding(top = 14.dp, bottom = 2.dp),
                    )
                    Skin.entries.filter { it.group == group }.forEach { skin -> SkinRow(skin, skin == current) }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.close)) } },
    )
}

/** One skin: its swatch, name and description; picking it applies it at once. */
@Composable
private fun SkinRow(skin: Skin, selected: Boolean) {
    val context = LocalContext.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .selectable(selected = selected, role = Role.RadioButton) {
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
