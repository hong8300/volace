package com.hong.volace.ui.schedule

import com.hong.volace.ui.alarm.ExactAlarmNotice
import com.hong.volace.ui.alarm.rememberExactAlarmAllowed
import com.hong.volace.ui.theme.cardShape
import com.hong.volace.ui.theme.ProfileIconView
import android.Manifest
import android.app.TimePickerDialog
import android.content.Context
import android.content.pm.PackageManager
import android.text.format.DateFormat
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DateRangePicker
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDateRangePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.hong.volace.R
import com.hong.volace.data.Profile
import com.hong.volace.data.ProfileDao
import com.hong.volace.data.ScheduleDao
import com.hong.volace.data.ScheduleRule
import com.hong.volace.data.ScheduleSkip
import com.hong.volace.data.icon
import com.hong.volace.schedule.ScheduleCalc
import com.hong.volace.schedule.ScheduleStatus
import com.hong.volace.schedule.Schedules
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Calendar
import java.util.Date

/** Rule id for "a new one" in the edit dialog (real ids start at 1). */
private const val NEW_RULE = 0L

/** Monday first, as the bits are (ScheduleCalc.dayBit). */
private val WEEK = DayOfWeek.entries

/**
 * "スケジュール": the rules (at a time, on some days, switch to a profile), the days off, what comes
 * next and how the last switch went (DESIGN.md 5.15).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScheduleScreen(profileDao: ProfileDao, scheduleDao: ScheduleDao, onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val profiles by remember(profileDao) { profileDao.observeAll() }.collectAsState(initial = emptyList())
    val rules by remember(scheduleDao) { scheduleDao.observeRules() }.collectAsState(initial = null)
    val skips by remember(scheduleDao) { scheduleDao.observeSkips() }.collectAsState(initial = emptyList())
    val status by remember(context) { Schedules.observeStatus(context) }.collectAsState(initial = null)
    val now = rememberMinuteClock()

    LaunchedEffect(scheduleDao) { scheduleDao.deleteSkipsBefore(LocalDate.now().toEpochDay()) }

    // The failure notification is the fallback when Android ignores a switch; ask once a rule exists.
    val notifications = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
    fun askForNotifications() {
        if (context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            notifications.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    /** Every edit re-arms the alarm; only boundaries from now on count. */
    fun change(block: suspend () -> Unit) {
        scope.launch {
            block()
            withContext(Dispatchers.IO) { Schedules.onRulesChanged(context) }
        }
    }

    // The rule whose dialog is open: NEW_RULE for a new one.
    var editingId by rememberSaveable { mutableStateOf<Long?>(null) }
    var addingSkip by rememberSaveable { mutableStateOf(false) }

    editingId?.let { id ->
        val rule = rules?.firstOrNull { it.id == id }
        if (id == NEW_RULE || rule != null) {
            RuleDialog(
                rule = rule,
                profiles = profiles,
                others = rules.orEmpty().filter { it.id != id },
                onSave = { saved ->
                    editingId = null
                    change { if (saved.id == NEW_RULE) scheduleDao.insert(saved) else scheduleDao.update(saved) }
                    askForNotifications()
                },
                onDelete = {
                    editingId = null
                    if (rule != null) change { scheduleDao.delete(rule) }
                },
                onDismiss = { editingId = null },
            )
        }
    }
    if (addingSkip) {
        SkipDialog(
            onSave = { from, to ->
                addingSkip = false
                change { scheduleDao.insert(ScheduleSkip(fromDay = from.toEpochDay(), toDay = to.toEpochDay())) }
            },
            onDismiss = { addingSkip = false },
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back_to_list))
                    }
                },
                title = { Text(stringResource(R.string.schedule_title), fontWeight = FontWeight.Bold) },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.padding(padding).fillMaxSize(),
            contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 4.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item(key = "intro") {
                Text(
                    stringResource(R.string.schedule_intro),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 6.dp),
                )
            }
            item(key = "exact-alarm") { ExactAlarmNotice(relevant = rules.orEmpty().any { it.enabled }) }
            item(key = "status") {
                StatusCard(rules.orEmpty(), skips, profiles, status, now)
            }
            item(key = "rules-header") { SectionHeader(stringResource(R.string.schedule_rules)) }
            if (rules?.isEmpty() == true) {
                item(key = "no-rules") { Hint(stringResource(R.string.schedule_no_rules)) }
            }
            items(rules.orEmpty(), key = { "rule-${it.id}" }) { rule ->
                RuleRow(
                    rule = rule,
                    profile = profiles.firstOrNull { it.id == rule.profileId },
                    onToggle = { on -> change { scheduleDao.update(rule.copy(enabled = on)) } },
                    onClick = { editingId = rule.id },
                )
            }
            item(key = "add-rule") {
                OutlinedButton(onClick = { editingId = NEW_RULE }, enabled = profiles.isNotEmpty()) {
                    Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.schedule_add_rule))
                }
            }
            item(key = "skips-header") {
                Column {
                    SectionHeader(stringResource(R.string.schedule_skips))
                    Hint(stringResource(R.string.schedule_skips_desc))
                }
            }
            if (skips.isEmpty()) {
                item(key = "no-skips") { Hint(stringResource(R.string.schedule_no_skips)) }
            }
            items(skips, key = { "skip-${it.id}" }) { skip ->
                SkipRow(skip, onRemove = { change { scheduleDao.delete(skip) } })
            }
            item(key = "add-skip") {
                OutlinedButton(onClick = { addingSkip = true }) {
                    Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.schedule_add_skip))
                }
            }
        }
    }
}

/** The list's way in: what the schedule does next, or that its last switch failed. */
@Composable
fun ScheduleSummaryCard(
    rules: List<ScheduleRule>,
    skips: List<ScheduleSkip>,
    profiles: List<Profile>,
    status: ScheduleStatus?,
    onClick: () -> Unit,
) {
    val context = LocalContext.current
    val now = rememberMinuteClock()
    val next = ScheduleCalc.next(rules, skips, now)
    val failed = status?.outcome?.failed == true
    val exactAlarmAllowed = rememberExactAlarmAllowed()
    Surface(
        onClick = onClick,
        shape = cardShape(),
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Filled.Schedule,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(22.dp),
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.schedule_title), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                val line = when {
                    rules.isEmpty() -> stringResource(R.string.schedule_list_empty)
                    next == null -> stringResource(R.string.schedule_list_off)
                    else -> stringResource(
                        R.string.schedule_list_next,
                        scheduleTimeText(context, next.at, now),
                        profileName(profiles, next.profileId),
                    )
                }
                Text(line, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (rules.isNotEmpty() && ScheduleCalc.isSkipped(LocalDate.now(), skips)) {
                    Text(
                        stringResource(R.string.schedule_list_skipping),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (failed && status != null) {
                    Text(statusText(status, now), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                }
                if (rules.any { it.enabled } && !exactAlarmAllowed) {
                    Text(
                        stringResource(R.string.exact_alarm_list_missing),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun StatusCard(
    rules: List<ScheduleRule>,
    skips: List<ScheduleSkip>,
    profiles: List<Profile>,
    status: ScheduleStatus?,
    now: Long,
) {
    val context = LocalContext.current
    val next = ScheduleCalc.next(rules, skips, now)
    Surface(
        shape = cardShape(),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
            Label(stringResource(R.string.schedule_next_label))
            Text(
                next?.let {
                    stringResource(R.string.schedule_next, scheduleTimeText(context, it.at, now), profileName(profiles, it.profileId))
                } ?: stringResource(R.string.schedule_next_none),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            if (status != null) {
                Spacer(Modifier.height(10.dp))
                Label(stringResource(R.string.schedule_last_label))
                Text(
                    statusText(status, now),
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (status.outcome.failed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                )
            }
        }
    }
}

@Composable
private fun statusText(status: ScheduleStatus, now: Long): String {
    val time = scheduleTimeText(LocalContext.current, status.at, now)
    return when (status.outcome) {
        ScheduleStatus.Outcome.APPLIED -> stringResource(R.string.schedule_last_applied, time, status.profileName)
        ScheduleStatus.Outcome.BY_HAND -> stringResource(R.string.schedule_last_by_hand, time, status.profileName)
        ScheduleStatus.Outcome.OVERRIDDEN -> stringResource(R.string.schedule_last_overridden, time, status.profileName)
        ScheduleStatus.Outcome.AFTER_TIMER -> stringResource(R.string.schedule_last_after_timer, time, status.profileName)
        ScheduleStatus.Outcome.FAILED -> stringResource(R.string.schedule_last_failed, time, status.profileName)
        ScheduleStatus.Outcome.NEEDS_ACCESS -> stringResource(R.string.schedule_last_access, time, status.profileName)
        ScheduleStatus.Outcome.PROFILE_GONE -> stringResource(R.string.schedule_last_gone, time)
    }
}

@Composable
private fun profileName(profiles: List<Profile>, id: Long): String =
    profiles.firstOrNull { it.id == id }?.name ?: stringResource(R.string.schedule_profile_gone)

@Composable
private fun RuleRow(rule: ScheduleRule, profile: Profile?, onToggle: (Boolean) -> Unit, onClick: () -> Unit) {
    val context = LocalContext.current
    val time = minuteText(context, rule.minuteOfDay)
    val days = daysLabel(rule.days)
    val target = profile?.let { stringResource(R.string.schedule_rule_to, it.name) } ?: stringResource(R.string.schedule_profile_gone)
    val dim = if (rule.enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
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
            Column(modifier = Modifier.weight(1f)) {
                Text(time, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold, color = dim)
                Text(days, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (profile != null) {
                        val accent = Color(profile.colorArgb)
                        Box(
                            modifier = Modifier.size(22.dp).background(accent.copy(alpha = 0.20f), CircleShape),
                            contentAlignment = Alignment.Center,
                        ) {
                            ProfileIconView(profile.icon, tint = accent, size = 14.dp)
                        }
                        Spacer(Modifier.width(6.dp))
                    }
                    Text(
                        target,
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (profile == null) MaterialTheme.colorScheme.error else dim,
                    )
                }
            }
            Switch(
                checked = rule.enabled,
                onCheckedChange = onToggle,
                modifier = Modifier.semantics { contentDescription = "$time $days $target" },
            )
        }
    }
}

@Composable
private fun SkipRow(skip: ScheduleSkip, onRemove: () -> Unit) {
    val context = LocalContext.current
    val from = LocalDate.ofEpochDay(skip.fromDay)
    val to = LocalDate.ofEpochDay(skip.toDay)
    val text = if (from == to) dateText(context, from)
    else stringResource(R.string.schedule_skip_range, dateText(context, from), dateText(context, to))
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(start = 16.dp, end = 4.dp).heightIn(min = 48.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(text, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            IconButton(onClick = onRemove) {
                Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.schedule_skip_remove, text))
            }
        }
    }
}

/** Adding or editing one rule: the time, the days and the profile. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RuleDialog(
    rule: ScheduleRule?,
    profiles: List<Profile>,
    others: List<ScheduleRule>,
    onSave: (ScheduleRule) -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    var minute by rememberSaveable { mutableIntStateOf(rule?.minuteOfDay ?: (22 * 60)) }
    var days by rememberSaveable { mutableIntStateOf(rule?.days ?: ScheduleCalc.ALL_DAYS) }
    var profileId by rememberSaveable {
        mutableLongStateOf(rule?.profileId?.takeIf { id -> profiles.any { it.id == id } } ?: profiles.firstOrNull()?.id ?: -1L)
    }
    val clash = others.any { it.enabled && it.minuteOfDay == minute && it.days and days != 0 }

    fun pickTime() {
        TimePickerDialog(
            context,
            { _, hour, min -> minute = hour * 60 + min },
            minute / 60,
            minute % 60,
            DateFormat.is24HourFormat(context),
        ).show()
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(if (rule == null) R.string.schedule_add_rule else R.string.schedule_edit_rule)) },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                Text(stringResource(R.string.schedule_rule_time), style = MaterialTheme.typography.titleSmall)
                TextButton(onClick = ::pickTime) {
                    Text(minuteText(context, minute), style = MaterialTheme.typography.headlineMedium)
                }
                Spacer(Modifier.height(6.dp))
                Text(stringResource(R.string.schedule_rule_days), style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.height(4.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(
                        ScheduleCalc.ALL_DAYS to R.string.schedule_every_day,
                        ScheduleCalc.WEEKDAYS to R.string.schedule_weekdays,
                        ScheduleCalc.WEEKEND to R.string.schedule_weekend,
                    ).forEach { (mask, label) ->
                        FilterChip(selected = days == mask, onClick = { days = mask }, label = { Text(stringResource(label)) })
                    }
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    WEEK.forEach { day ->
                        val bit = ScheduleCalc.dayBit(day)
                        val fullName = dayName(day, TextStyle.FULL)
                        FilterChip(
                            selected = days and bit != 0,
                            onClick = { days = days xor bit },
                            label = { Text(dayName(day, TextStyle.SHORT)) },
                            modifier = Modifier.semantics { contentDescription = fullName },
                        )
                    }
                }
                if (days == 0) {
                    Text(stringResource(R.string.schedule_rule_no_days), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                } else if (clash) {
                    Text(stringResource(R.string.schedule_rule_same_time), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Spacer(Modifier.height(10.dp))
                Text(stringResource(R.string.schedule_rule_profile), style = MaterialTheme.typography.titleSmall)
                Column(modifier = Modifier.selectableGroup()) {
                    profiles.forEach { profile ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 48.dp)
                                .selectable(selected = profileId == profile.id, onClick = { profileId = profile.id }, role = Role.RadioButton),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(selected = profileId == profile.id, onClick = null)
                            Spacer(Modifier.width(8.dp))
                            ProfileIconView(profile.icon, tint = Color(profile.colorArgb), size = 18.dp)
                            Spacer(Modifier.width(6.dp))
                            Text(profile.name, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
                if (rule != null) {
                    Spacer(Modifier.height(6.dp))
                    TextButton(onClick = onDelete) {
                        Text(stringResource(R.string.schedule_rule_delete), color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = days != 0 && profiles.any { it.id == profileId },
                onClick = {
                    onSave(
                        (rule ?: ScheduleRule(id = NEW_RULE, minuteOfDay = minute, days = days, profileId = profileId))
                            .copy(minuteOfDay = minute, days = days, profileId = profileId, enabled = true),
                    )
                },
            ) { Text(stringResource(R.string.save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

/** Picks days off: a range, or a single day (only a start). Days before today cannot be picked. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SkipDialog(onSave: (LocalDate, LocalDate) -> Unit, onDismiss: () -> Unit) {
    val today = remember { LocalDate.now() }
    val state = rememberDateRangePickerState(
        selectableDates = object : SelectableDates {
            override fun isSelectableDate(utcTimeMillis: Long): Boolean = utcDate(utcTimeMillis) >= today
            override fun isSelectableYear(year: Int): Boolean = year >= today.year
        },
    )
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                enabled = state.selectedStartDateMillis != null,
                onClick = {
                    val from = utcDate(state.selectedStartDateMillis ?: return@TextButton)
                    onSave(from, state.selectedEndDateMillis?.let(::utcDate) ?: from)
                },
            ) { Text(stringResource(R.string.save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    ) {
        DateRangePicker(
            state = state,
            title = {
                Text(
                    stringResource(R.string.schedule_skip_dialog_title),
                    modifier = Modifier.padding(start = 24.dp, end = 12.dp, top = 16.dp),
                )
            },
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(start = 6.dp, top = 8.dp),
    )
}

@Composable
private fun Label(text: String) {
    Text(text, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun Hint(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 6.dp),
    )
}

/** The time now, moved on at every minute: "next" changes once a boundary has passed. */
@Composable
private fun rememberMinuteClock(): Long {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(60_000 - System.currentTimeMillis() % 60_000 + 500)
            now = System.currentTimeMillis()
        }
    }
    return now
}

/** The date pickers work in UTC midnights. */
private fun utcDate(utcMillis: Long): LocalDate = Instant.ofEpochMilli(utcMillis).atZone(ZoneOffset.UTC).toLocalDate()

@Composable
private fun dayName(day: DayOfWeek, style: TextStyle): String =
    day.getDisplayName(style, LocalConfiguration.current.locales[0])

@Composable
private fun daysLabel(days: Int): String = when (days) {
    ScheduleCalc.ALL_DAYS -> stringResource(R.string.schedule_every_day)
    ScheduleCalc.WEEKDAYS -> stringResource(R.string.schedule_weekdays)
    ScheduleCalc.WEEKEND -> stringResource(R.string.schedule_weekend)
    else -> WEEK.filter { days and ScheduleCalc.dayBit(it) != 0 }
        .map { dayName(it, TextStyle.SHORT) }
        .joinToString(stringResource(R.string.list_separator))
}

/** "22:00" (or "10:00 PM"), following the 24-hour setting. */
internal fun minuteText(context: Context, minuteOfDay: Int): String {
    val time = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, minuteOfDay / 60)
        set(Calendar.MINUTE, minuteOfDay % 60)
    }
    return DateFormat.getTimeFormat(context).format(time.time)
}

/** "10月6日(月)" / "Mon, Oct 6". */
internal fun dateText(context: Context, date: LocalDate): String {
    val locale = context.resources.configuration.locales[0]
    return DateTimeFormatter.ofPattern(DateFormat.getBestDateTimePattern(locale, "MMMdEEE"), locale).format(date)
}

/** "7:00" today, "明日 7:00", "昨日 22:00", or with the date further away. */
internal fun scheduleTimeText(context: Context, at: Long, now: Long): String {
    val zone = ZoneId.systemDefault()
    val day = Instant.ofEpochMilli(at).atZone(zone).toLocalDate()
    val today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
    val time = DateFormat.getTimeFormat(context).format(Date(at))
    return when (day) {
        today -> time
        today.plusDays(1) -> context.getString(R.string.timer_tomorrow, time)
        today.minusDays(1) -> context.getString(R.string.schedule_yesterday, time)
        else -> "${dateText(context, day)} $time"
    }
}

/** For the delete confirmation of a profile. */
@Composable
fun scheduleDeleteNote(count: Int): String? =
    if (count == 0) null else pluralStringResource(R.plurals.schedule_delete_rules, count, count)
