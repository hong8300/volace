package com.hong.volace.ui.bluetooth

import com.hong.volace.ui.theme.cardShape
import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.pm.PackageManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.hong.volace.R
import com.hong.volace.bluetooth.BluetoothStatus
import com.hong.volace.bluetooth.BluetoothSwitch
import com.hong.volace.data.BluetoothRule
import com.hong.volace.data.BluetoothRuleDao
import com.hong.volace.data.Profile
import com.hong.volace.data.ProfileDao
import kotlinx.coroutines.launch

/** A device paired with the phone, as the add dialog lists it. */
private data class PairedDevice(val address: String, val name: String)

/** The rule whose dialog is open: [NEW] for a new one. */
private const val NEW = 0L

/**
 * "Bluetooth": which profile a device switches to when it connects, and what happens when it
 * disconnects (DESIGN.md 5.18).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BluetoothScreen(profileDao: ProfileDao, ruleDao: BluetoothRuleDao, onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val profiles by remember(profileDao) { profileDao.observeAll() }.collectAsState(initial = emptyList())
    val rules by remember(ruleDao) { ruleDao.observeAll() }.collectAsState(initial = null)
    val status by remember(context) { BluetoothSwitch.observeStatus(context) }.collectAsState(initial = null)

    var granted by remember { mutableStateOf(hasPermission(context)) }
    LifecycleResumeEffect(Unit) {
        granted = hasPermission(context)
        onPauseOrDispose {}
    }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted = it }

    var editingId by rememberSaveable { mutableStateOf<Long?>(null) }
    editingId?.let { id ->
        val rule = rules?.firstOrNull { it.id == id }
        if (id == NEW || rule != null) {
            val taken = rules.orEmpty().filter { it.id != id }.map { it.address }.toSet()
            RuleDialog(
                rule = rule,
                devices = remember(granted) { pairedDevices(context) }.filter { it.address !in taken },
                profiles = profiles,
                onSave = { saved ->
                    editingId = null
                    scope.launch { if (saved.id == NEW) ruleDao.insert(saved) else ruleDao.update(saved) }
                },
                onDelete = {
                    editingId = null
                    if (rule != null) scope.launch { ruleDao.delete(rule) }
                },
                onDismiss = { editingId = null },
            )
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back_to_list))
                    }
                },
                title = { Text(stringResource(R.string.bt_title), fontWeight = FontWeight.Bold) },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.padding(padding).fillMaxSize(),
            contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 4.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item(key = "intro") { Hint(stringResource(R.string.bt_intro)) }
            if (!granted) {
                item(key = "permission") {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.errorContainer,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                            Text(
                                stringResource(R.string.bt_permission),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                            )
                            TextButton(onClick = { permission.launch(Manifest.permission.BLUETOOTH_CONNECT) }) {
                                Text(stringResource(R.string.bt_permission_button))
                            }
                        }
                    }
                }
            }
            status?.let { last ->
                item(key = "status") {
                    Surface(
                        shape = cardShape(),
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
                            Text(
                                stringResource(R.string.bt_last_label),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text(
                                statusText(last),
                                style = MaterialTheme.typography.bodyMedium,
                                color = if (last.outcome.failed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                            )
                        }
                    }
                }
            }
            item(key = "rules-header") {
                Text(
                    stringResource(R.string.bt_rules),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(start = 6.dp, top = 8.dp),
                )
            }
            if (rules?.isEmpty() == true) {
                item(key = "no-rules") { Hint(stringResource(R.string.bt_no_rules)) }
            }
            items(rules.orEmpty(), key = { "rule-${it.id}" }) { rule ->
                RuleRow(
                    rule = rule,
                    profiles = profiles,
                    onToggle = { on -> scope.launch { ruleDao.update(rule.copy(enabled = on)) } },
                    onClick = { editingId = rule.id },
                )
            }
            item(key = "add") {
                OutlinedButton(onClick = { editingId = NEW }, enabled = granted && profiles.isNotEmpty()) {
                    Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.bt_add))
                }
            }
        }
    }
}

/** The list's way in: the devices set up, or that the last switch failed. */
@Composable
fun BluetoothSummaryCard(rules: List<BluetoothRule>, profiles: List<Profile>, status: BluetoothStatus?, onClick: () -> Unit) {
    val failed = status?.outcome?.failed == true
    Surface(
        onClick = onClick,
        shape = cardShape(),
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.Bluetooth, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.bt_title), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                val first = rules.firstOrNull()
                val line = if (first == null) {
                    stringResource(R.string.bt_list_empty)
                } else {
                    listOfNotNull(
                        stringResource(R.string.bt_list_rule, first.name, profileName(profiles, first.profileId)),
                        (rules.size - 1).takeIf { it > 0 }?.let { stringResource(R.string.bt_list_more, it) },
                    ).joinToString(stringResource(R.string.list_separator))
                }
                Text(line, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (failed && status != null) {
                    Text(statusText(status), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                }
            }
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun statusText(status: BluetoothStatus): String = when (status.outcome) {
    BluetoothStatus.Outcome.APPLIED -> stringResource(R.string.bt_last_applied, status.deviceName, status.profileName)
    BluetoothStatus.Outcome.RESTORED -> stringResource(R.string.bt_last_restored, status.deviceName, status.profileName)
    BluetoothStatus.Outcome.AFTER_TIMER -> stringResource(R.string.bt_last_after_timer, status.deviceName, status.profileName)
    BluetoothStatus.Outcome.KEPT -> stringResource(R.string.bt_last_kept, status.deviceName)
    BluetoothStatus.Outcome.BY_HAND -> stringResource(R.string.bt_last_by_hand, status.deviceName, status.profileName)
    BluetoothStatus.Outcome.OVERRIDDEN -> stringResource(R.string.bt_last_overridden, status.deviceName, status.profileName)
    BluetoothStatus.Outcome.FAILED -> stringResource(
        if (status.connected) R.string.bt_last_failed_connected else R.string.bt_last_failed_disconnected,
        status.deviceName,
        status.profileName,
    )
    BluetoothStatus.Outcome.NEEDS_ACCESS -> stringResource(R.string.bt_last_access, status.deviceName)
    BluetoothStatus.Outcome.PROFILE_GONE -> stringResource(R.string.bt_last_gone, status.deviceName)
}

@Composable
private fun profileName(profiles: List<Profile>, id: Long?): String =
    profiles.firstOrNull { it.id == id }?.name ?: stringResource(R.string.schedule_profile_gone)

@Composable
private fun RuleRow(rule: BluetoothRule, profiles: List<Profile>, onToggle: (Boolean) -> Unit, onClick: () -> Unit) {
    val connect = stringResource(R.string.bt_rule_connect, profileName(profiles, rule.profileId))
    val disconnect = when (rule.onDisconnect) {
        BluetoothRule.DISCONNECT_PROFILE -> stringResource(R.string.bt_rule_profile, profileName(profiles, rule.disconnectProfileId))
        BluetoothRule.DISCONNECT_NOTHING -> stringResource(R.string.bt_rule_nothing)
        else -> stringResource(R.string.bt_rule_restore)
    }
    val detail = listOf(connect, disconnect).joinToString(stringResource(R.string.list_separator))
    Surface(
        onClick = onClick,
        shape = cardShape(),
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(start = 16.dp, end = 12.dp, top = 12.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Filled.Bluetooth, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(rule.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(detail, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Switch(
                checked = rule.enabled,
                onCheckedChange = onToggle,
                modifier = Modifier.semantics { contentDescription = "${rule.name} $detail" },
            )
        }
    }
}

/** Adding or editing a device's rule: the device (new rules only), its profile, the disconnect. */
@Composable
private fun RuleDialog(
    rule: BluetoothRule?,
    devices: List<PairedDevice>,
    profiles: List<Profile>,
    onSave: (BluetoothRule) -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
) {
    var address by rememberSaveable { mutableStateOf(rule?.address ?: devices.firstOrNull()?.address) }
    var profileId by rememberSaveable { mutableStateOf(rule?.profileId?.takeIf { id -> profiles.any { it.id == id } } ?: profiles.firstOrNull()?.id) }
    var onDisconnect by rememberSaveable { mutableIntStateOf(rule?.onDisconnect ?: BluetoothRule.DISCONNECT_RESTORE) }
    var disconnectProfileId by rememberSaveable { mutableStateOf(rule?.disconnectProfileId) }
    val name = rule?.name ?: devices.firstOrNull { it.address == address }?.name

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(if (rule == null) R.string.bt_add else R.string.bt_edit)) },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                Label(stringResource(R.string.bt_device))
                if (rule != null) {
                    Text(rule.name, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(vertical = 8.dp))
                } else if (devices.isEmpty()) {
                    Text(
                        stringResource(R.string.bt_no_devices),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(vertical = 8.dp),
                    )
                } else {
                    Column(Modifier.selectableGroup()) {
                        devices.forEach { device ->
                            Choice(device.name, address == device.address) { address = device.address }
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                Label(stringResource(R.string.bt_connect_profile))
                Column(Modifier.selectableGroup()) {
                    profiles.forEach { profile ->
                        Choice(profile.name, profileId == profile.id) { profileId = profile.id }
                    }
                }
                Spacer(Modifier.height(8.dp))
                Label(stringResource(R.string.bt_disconnect))
                Column(Modifier.selectableGroup()) {
                    Choice(stringResource(R.string.bt_disconnect_restore), onDisconnect == BluetoothRule.DISCONNECT_RESTORE) {
                        onDisconnect = BluetoothRule.DISCONNECT_RESTORE
                    }
                    profiles.forEach { profile ->
                        Choice(
                            stringResource(R.string.bt_disconnect_profile, profile.name),
                            onDisconnect == BluetoothRule.DISCONNECT_PROFILE && disconnectProfileId == profile.id,
                        ) {
                            onDisconnect = BluetoothRule.DISCONNECT_PROFILE
                            disconnectProfileId = profile.id
                        }
                    }
                    Choice(stringResource(R.string.bt_disconnect_nothing), onDisconnect == BluetoothRule.DISCONNECT_NOTHING) {
                        onDisconnect = BluetoothRule.DISCONNECT_NOTHING
                    }
                }
                if (rule != null) {
                    TextButton(onClick = onDelete) { Text(stringResource(R.string.bt_delete), color = MaterialTheme.colorScheme.error) }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = address != null && name != null && profileId != null,
                onClick = {
                    onSave(
                        BluetoothRule(
                            id = rule?.id ?: NEW,
                            address = address ?: return@TextButton,
                            name = name ?: return@TextButton,
                            profileId = profileId ?: return@TextButton,
                            onDisconnect = onDisconnect,
                            disconnectProfileId = disconnectProfileId.takeIf { onDisconnect == BluetoothRule.DISCONNECT_PROFILE },
                            enabled = rule?.enabled ?: true,
                        ),
                    )
                },
            ) { Text(stringResource(R.string.save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

@Composable
private fun Choice(label: String, selected: Boolean, onSelect: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .selectable(selected = selected, onClick = onSelect, role = Role.RadioButton),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = null)
        Spacer(Modifier.width(8.dp))
        Text(label, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun Label(text: String) {
    Text(text, style = MaterialTheme.typography.titleSmall)
}

@Composable
private fun Hint(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 6.dp),
    )
}

private fun hasPermission(context: Context): Boolean =
    context.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED

@SuppressLint("MissingPermission") // checked: empty without it
private fun pairedDevices(context: Context): List<PairedDevice> {
    if (!hasPermission(context)) return emptyList()
    val adapter = context.getSystemService(BluetoothManager::class.java)?.adapter ?: return emptyList()
    return runCatching {
        adapter.bondedDevices.map { PairedDevice(it.address, it.alias ?: it.name ?: it.address) }.sortedBy { it.name.lowercase() }
    }.getOrDefault(emptyList())
}
