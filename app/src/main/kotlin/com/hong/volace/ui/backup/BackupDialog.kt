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
                "${profiles.size} 件のプロファイルを保存しました"
            }.getOrElse { "保存できませんでした（${it.message}）" }
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
                        onMessage("ファイルにプロファイルがありません")
                        onDismiss()
                    } else {
                        pending = profiles
                    }
                }
                .onFailure {
                    onMessage("読み込めませんでした: ${it.message}")
                    onDismiss()
                }
        }
    }

    val toImport = pending
    if (toImport != null) {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("${toImport.size} 件のプロファイルを読み込む") },
            text = {
                Text(
                    "今のプロファイルに追加するか、すべて置き換えるかを選んでください。" +
                        "音量はこの端末の範囲に合わせます。読み込んだだけでは適用しません。",
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch { onMessage(import(dao, volumeApplier, toImport, replace = true)); onDismiss() }
                }) { Text("置き換える", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = {
                    scope.launch { onMessage(import(dao, volumeApplier, toImport, replace = false)); onDismiss() }
                }) { Text("追加する") }
            },
        )
        return
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("バックアップ") },
        text = {
            Column {
                Choice("ファイルに保存", "すべてのプロファイルを JSON ファイルに書き出します") {
                    save.launch("volace-profiles-${LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE)}.json")
                }
                Choice("ファイルから復元", "保存したファイルからプロファイルを読み込みます") {
                    // Some providers label JSON as plain text or as a generic binary.
                    open.launch(arrayOf("application/json", "text/plain", "application/octet-stream"))
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("閉じる") } },
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
    return if (replace) "${prepared.size} 件のプロファイルに置き換えました" else "${prepared.size} 件のプロファイルを追加しました"
}

private suspend fun write(context: Context, uri: Uri, text: String) = withContext(Dispatchers.IO) {
    context.contentResolver.openOutputStream(uri, "wt")!!.use { it.write(text.toByteArray()) }
}

private suspend fun read(context: Context, uri: Uri): String = withContext(Dispatchers.IO) {
    context.contentResolver.openInputStream(uri)!!.use { it.readBytes().decodeToString() }
}
