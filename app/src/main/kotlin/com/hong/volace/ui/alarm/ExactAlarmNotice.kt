package com.hong.volace.ui.alarm

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.hong.volace.R
import com.hong.volace.alarm.ExactAlarms

/**
 * Whether "アラームとリマインダー" is allowed, read again whenever the screen comes back (the user
 * sets it in Settings and returns).
 */
@Composable
fun rememberExactAlarmAllowed(): Boolean {
    val context = LocalContext.current
    var allowed by remember { mutableStateOf(ExactAlarms.isAllowed(context)) }
    LifecycleResumeEffect(Unit) {
        allowed = ExactAlarms.isAllowed(context)
        onPauseOrDispose {}
    }
    return allowed
}

/**
 * Says why "アラームとリマインダー" is needed and opens its Settings page. Nothing when it is
 * allowed. [relevant] is false where nothing depends on it yet (e.g. no rules), so it does not
 * nag before the feature is used. [text] says what the feature loses without it.
 */
@Composable
fun ExactAlarmNotice(
    modifier: Modifier = Modifier,
    relevant: Boolean = true,
    @StringRes text: Int = R.string.exact_alarm_notice,
) {
    val allowed = rememberExactAlarmAllowed()
    if (allowed || !relevant) return
    val context = LocalContext.current
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.errorContainer,
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
            Text(
                stringResource(text),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onErrorContainer,
            )
            TextButton(onClick = { context.startActivity(ExactAlarms.settingsIntent(context)) }) {
                Text(stringResource(R.string.exact_alarm_open_settings))
            }
        }
    }
}
