package com.hong.volace.tile

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import com.hong.volace.MainActivity
import com.hong.volace.audio.ApplyResult
import com.hong.volace.audio.VolumeApplier
import com.hong.volace.audio.message
import com.hong.volace.data.Profile
import com.hong.volace.data.VolaceDatabase
import com.hong.volace.data.icon
import com.hong.volace.ui.theme.VolaceTheme
import com.hong.volace.widget.WidgetRefresher
import com.hong.volace.widget.WidgetState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * The small chooser the Quick Settings tile opens: every profile by name, tap to apply. It is a
 * real (translucent) activity so the volume change comes from a visible activity (Android 17).
 */
class ProfilePickerActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            VolaceTheme {
                Picker(
                    onApply = ::apply,
                    onOpenApp = ::openApp,
                    onDismiss = ::finish,
                )
            }
        }
    }

    private suspend fun apply(profile: Profile) {
        val result = withContext(Dispatchers.IO) {
            VolumeApplier(this@ProfilePickerActivity).apply(profile).also {
                if (it == ApplyResult.Applied) {
                    VolaceDatabase.get(applicationContext).profileDao().applyActive(profile.id)
                }
            }
        }
        WidgetRefresher.request(this)
        Toast.makeText(this, result.message(profile.name), Toast.LENGTH_SHORT).show()
        finish()
    }

    private fun openApp() {
        startActivity(
            Intent(this, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
        )
        finish()
    }
}

@Composable
private fun Picker(
    onApply: suspend (Profile) -> Unit,
    onOpenApp: () -> Unit,
    onDismiss: () -> Unit,
) {
    var state by remember { mutableStateOf<WidgetState?>(null) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    LaunchedEffect(Unit) {
        state = withContext(Dispatchers.IO) { WidgetState.load(context.applicationContext) }
    }

    // Scrim: tapping outside the card closes it.
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0x99000000))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                onDismiss()
            },
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier
                .padding(24.dp)
                .widthIn(max = 420.dp)
                .fillMaxWidth()
                // Swallow taps on the card itself.
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {},
        ) {
            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 18.dp)) {
                Text(
                    "プロファイルを選ぶ",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(start = 8.dp, bottom = 8.dp),
                )
                val current = state
                when {
                    current == null -> Unit
                    !current.hasAccess -> Text(
                        "「サイレント モードへのアクセス」が許可されていないため適用できません。" +
                            "「アプリを開く」から許可してください。",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(8.dp),
                    )
                    current.profiles.isEmpty() -> Text(
                        "プロファイルがありません。「アプリを開く」から作成してください。",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(8.dp),
                    )
                    else -> Column(
                        modifier = Modifier.verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        current.profiles.forEach { profile ->
                            PickerRow(profile, drifted = profile.isActive && current.drifted) {
                                scope.launch { onApply(profile) }
                            }
                        }
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    TextButton(onClick = onOpenApp) { Text("アプリを開く") }
                    TextButton(onClick = onDismiss) { Text("閉じる") }
                }
            }
        }
    }
}

@Composable
private fun PickerRow(profile: Profile, drifted: Boolean, onClick: () -> Unit) {
    val accent = Color(profile.colorArgb)
    val active = profile.isActive && !drifted
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = if (active) accent.copy(alpha = 0.16f) else MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.clickable(onClick = onClick).padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier.size(40.dp).clip(CircleShape)
                    .background(if (active) accent else accent.copy(alpha = 0.20f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = painterResource(profile.icon.res),
                    contentDescription = null,
                    tint = if (active) Color.White else accent,
                    modifier = Modifier.size(22.dp),
                )
            }
            Spacer(Modifier.width(12.dp))
            Text(
                profile.name,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f),
            )
            when {
                active -> Text("適用中", color = accent, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                drifted -> Text("変更あり", color = accent, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}
