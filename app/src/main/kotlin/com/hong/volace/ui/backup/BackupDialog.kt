package com.hong.volace.ui.backup

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.hong.volace.audio.VolumeApplier
import com.hong.volace.audio.ranges
import com.hong.volace.data.Profile
import com.hong.volace.data.ProfileBackup
import com.hong.volace.data.ProfileDao
import com.hong.volace.widget.WidgetRefresher
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.hong.volace.R

/**
 * Save every profile to a JSON file, or read one back (adding to or replacing the list). Files go
 * through the system picker (Storage Access Framework), so no storage permission is needed.
 */
@Composable
fun BackupDialog(
    dao: ProfileDao,
    volumeApplier: VolumeApplier,
    onDismiss: () -> Unit,
    onMessage: (String) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var pending by remember { mutableStateOf<List<Profile>?>(null) }

    val save = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val message = runCatching {
                val profiles = dao.getAllOnce()
                write(context, uri, ProfileBackup.toJson(profiles))
                context.resources.getQuantityString(R.plurals.backup_saved, profiles.size, profiles.size)
            }.getOrElse { context.getString(R.string.backup_save_failed, it.message.orEmpty()) }
            onMessage(message)
            onDismiss()
        }
    }

    val open = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            runCatching { ProfileBackup.fromJson(read(context, uri)) }
                .onSuccess { profiles ->
                    if (profiles.isEmpty()) {
                        onMessage(context.getString(R.string.backup_file_empty))
                        onDismiss()
                    } else {
                        pending = profiles
                    }
                }
                .onFailure {
                    val reason = (it as? ProfileBackup.FormatException)?.describe(context) ?: it.message.orEmpty()
                    onMessage(context.getString(R.string.backup_read_failed, reason))
                    onDismiss()
                }
        }
    }

    val toImport = pending
    if (toImport != null) {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text(pluralStringResource(R.plurals.backup_import_title, toImport.size, toImport.size)) },
            text = { Text(stringResource(R.string.backup_import_body)) },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch { onMessage(import(dao, volumeApplier, toImport, replace = true)); onDismiss() }
                }) { Text(stringResource(R.string.backup_replace), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = {
                    scope.launch { onMessage(import(dao, volumeApplier, toImport, replace = false)); onDismiss() }
                }) { Text(stringResource(R.string.backup_add)) }
            },
        )
        return
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.backup)) },
        text = {
            Column {
                Choice(stringResource(R.string.backup_save), stringResource(R.string.backup_save_desc)) {
                    save.launch("volace-profiles-${LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE)}.json")
                }
                Choice(stringResource(R.string.backup_restore), stringResource(R.string.backup_restore_desc)) {
                    // Some providers label JSON as plain text or as a generic binary.
                    open.launch(arrayOf("application/json", "text/plain", "application/octet-stream"))
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.close)) } },
    )
}

@Composable
private fun Choice(title: String, subtitle: String, onClick: () -> Unit) {
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

private suspend fun import(
    dao: ProfileDao,
    volumeApplier: VolumeApplier,
    profiles: List<Profile>,
    replace: Boolean,
): String {
    val ranges = volumeApplier.ranges()
    val first = if (replace) 0 else dao.nextOrderIndex()
    val prepared = profiles.mapIndexed { i, p -> ranges.normalize(p.copy(orderIndex = first + i)) }
    if (replace) dao.replaceAll(prepared) else dao.insertAll(prepared)
    WidgetRefresher.request(volumeApplier.context)
    val res = volumeApplier.context.resources
    return res.getQuantityString(
        if (replace) R.plurals.backup_replaced else R.plurals.backup_added,
        prepared.size,
        prepared.size,
    )
}

private suspend fun write(context: Context, uri: Uri, text: String) = withContext(Dispatchers.IO) {
    context.contentResolver.openOutputStream(uri, "wt")!!.use { it.write(text.toByteArray()) }
}

private suspend fun read(context: Context, uri: Uri): String = withContext(Dispatchers.IO) {
    context.contentResolver.openInputStream(uri)!!.use { it.readBytes().decodeToString() }
}
