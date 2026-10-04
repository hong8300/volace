package com.hong.volace.audio

import android.app.AutomaticZenRule
import android.app.NotificationManager
import android.content.ComponentName
import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import android.os.Build
import android.service.notification.Condition
import android.service.notification.ZenPolicy
import android.util.Log
import androidx.annotation.StringRes
import androidx.core.content.edit
import com.hong.volace.MainActivity
import com.hong.volace.R

private const val TAG = "VolaceDnd"

/**
 * What a profile does with "Do Not Disturb" (DESIGN.md 5.17). [OFF] turns Volace's own modes off
 * and leaves every other mode (Bedtime, driving, one turned on by hand) as it is.
 */
enum class DndMode(
    /** Stored in the database and in backups' "dnd". Never renumber. */
    val value: Int,
    val key: String,
    @StringRes val label: Int,
    @StringRes val description: Int,
) {
    OFF(0, "off", R.string.dnd_off, R.string.dnd_off_desc),
    PRIORITY(1, "priority", R.string.dnd_priority, R.string.dnd_priority_desc),
    ALARMS(2, "alarms", R.string.dnd_alarms, R.string.dnd_alarms_desc),
    ;

    /**
     * What the mode lets through. Both are "priority" modes: Android's own "alarms only" filter
     * forces the ringer to silent and, when another mode was on at the same time, did not bring it
     * back afterwards (DESIGN.md 8.9). A priority mode allowing alarms and media only sounds the
     * same and leaves the ringer alone. Null: the phone's Do Not Disturb settings.
     */
    fun policy(): ZenPolicy? = when (this) {
        ALARMS -> ZenPolicy.Builder().disallowAllSounds().allowAlarms(true).allowMedia(true).build()
        else -> null
    }

    companion object {
        fun of(value: Int): DndMode = entries.firstOrNull { it.value == value } ?: OFF
        fun ofKey(key: String?): DndMode = entries.firstOrNull { it.key == key } ?: OFF
    }
}

/** Who asked for a switch, for Android's own bookkeeping of the modes ([Condition.source]). */
enum class SwitchSource {
    USER,
    SCHEDULE,

    /** A Bluetooth device connected or disconnected. */
    CONTEXT,
}

/** "Do Not Disturb" as it is now, for the screens. */
data class DndState(
    /** NotificationManager.INTERRUPTION_FILTER_*: ALL means off. */
    val filter: Int,
    /** Whether alarms still sound (always with ALARMS, never with NONE, per policy with PRIORITY). */
    val alarmsAllowed: Boolean,
    /** Volace turned it on: one of its own modes, or "サイレント" (Android adds DND to it). */
    val byVolace: Boolean,
    /** Volace's own mode that is on, if any. */
    val volaceMode: DndMode = DndMode.OFF,
) {
    val active: Boolean get() = filter != NotificationManager.INTERRUPTION_FILTER_ALL

    /** "おやすみモード: 重要な通知のみ・アラームは鳴る" (with "ほかのモード" when not Volace's). */
    fun describe(context: Context): String {
        if (!active) return context.getString(R.string.dnd_state_off)
        val kind = context.getString(
            when {
                // Volace's "アラームのみ" is a priority mode (DndMode.policy): say what it lets through.
                volaceMode == DndMode.ALARMS -> R.string.dnd_kind_alarms
                filter == NotificationManager.INTERRUPTION_FILTER_ALARMS -> R.string.dnd_kind_alarms
                filter == NotificationManager.INTERRUPTION_FILTER_NONE -> R.string.dnd_kind_none
                alarmsAllowed -> R.string.dnd_kind_priority_alarms
                else -> R.string.dnd_kind_priority_no_alarms
            },
        )
        return context.getString(if (byVolace) R.string.dnd_state_on else R.string.dnd_state_by_others, kind)
    }

    /** "おやすみ（アラーム可）" for the widgets' status line; null while off. */
    fun shortText(context: Context): String? = if (!active) null else context.getString(
        R.string.dnd_short_kind,
        context.getString(if (alarmsAllowed) R.string.dnd_short_alarms_on else R.string.dnd_short_alarms_off),
    )

    companion object {
        val OFF = DndState(NotificationManager.INTERRUPTION_FILTER_ALL, alarmsAllowed = true, byVolace = false)
    }
}

/**
 * Volace's own two modes ("Volace（重要な通知のみ）", "Volace（アラームのみ）"), registered as
 * AutomaticZenRules and turned on and off as profiles ask. Two rules rather than one whose filter
 * changes: Android keeps whatever the user customised in a rule over the app's later updates.
 *
 * Also keeps track of "Do Not Disturb" that Volace caused by applying "サイレント" (Android turns
 * it on with the silent ringer). Only that one is Volace's to end: switching the ringer back to
 * "着信音" or "バイブ" ends DND as a whole, every other active mode included (DESIGN.md 8.9).
 */
class DndModes(context: Context) {

    private val app = context.applicationContext
    private val notifications = app.getSystemService(NotificationManager::class.java)
    private val prefs: SharedPreferences = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    val filter: Int get() = notifications.currentInterruptionFilter

    val active: Boolean get() = filter != NotificationManager.INTERRUPTION_FILTER_ALL

    /** The DND now on came from Volace's "サイレント" (and was not turned off since). */
    var silentByVolace: Boolean
        get() = active && prefs.getBoolean(KEY_SILENT, false)
        set(value) = prefs.edit(commit = true) { putBoolean(KEY_SILENT, value) }

    /** Which of Volace's own modes is on. */
    fun current(): DndMode {
        if (!active) return DndMode.OFF
        return DndMode.entries.firstOrNull { mode -> mode != DndMode.OFF && isOn(mode) } ?: DndMode.OFF
    }

    fun state(): DndState {
        val filter = filter
        val alarms = when (filter) {
            NotificationManager.INTERRUPTION_FILTER_ALL, NotificationManager.INTERRUPTION_FILTER_ALARMS -> true
            NotificationManager.INTERRUPTION_FILTER_NONE -> false
            else -> notifications.consolidatedNotificationPolicy.priorityCategories and
                NotificationManager.Policy.PRIORITY_CATEGORY_ALARMS != 0
        }
        val own = current()
        return DndState(filter, alarms, byVolace = own != DndMode.OFF || silentByVolace, volaceMode = own)
    }

    /** Turns both of Volace's modes off. Other modes are not touched. */
    fun turnOffOwn(source: SwitchSource) {
        DndMode.entries.filter { it != DndMode.OFF }.forEach { mode ->
            val id = prefs.getString(ruleKey(mode), null) ?: return@forEach
            runCatching { notifications.setAutomaticZenRuleState(id, condition(mode, on = false, source)) }
                .onFailure { Log.w(TAG, "could not turn off $mode", it) }
        }
    }

    /**
     * Turns [mode] on (after [turnOffOwn]: setting it off and on again also overrides a pause the
     * user put on it from the system's Modes). Throws when Android refuses.
     */
    fun turnOn(mode: DndMode, source: SwitchSource) {
        if (mode == DndMode.OFF) return
        notifications.setAutomaticZenRuleState(ruleId(mode), condition(mode, on = true, source))
    }

    /**
     * Removes Volace's modes no profile uses any more: they would sit in the system's Modes list
     * (and one still on would never be turned off again).
     */
    fun removeUnused(used: Set<DndMode>) {
        DndMode.entries.filter { it != DndMode.OFF && it !in used }.forEach { mode ->
            val key = ruleKey(mode)
            val id = prefs.getString(key, null) ?: return@forEach
            runCatching { notifications.removeAutomaticZenRule(id) }
                .onFailure { Log.w(TAG, "could not remove $mode", it) }
            prefs.edit(commit = true) { remove(key) }
        }
    }

    private fun isOn(mode: DndMode): Boolean {
        val id = prefs.getString(ruleKey(mode), null) ?: return false
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM) {
            return runCatching { notifications.getAutomaticZenRuleState(id) == Condition.STATE_TRUE }.getOrDefault(false)
        }
        // API 33/34 cannot ask; the filter says DND is on, and the last one Volace turned on is it.
        return prefs.getString(KEY_LAST_ON, null) == mode.key
    }

    /** The rule for [mode], registered again when the user deleted it from the system's Modes. */
    private fun ruleId(mode: DndMode): String {
        prefs.getString(ruleKey(mode), null)?.let { id ->
            val existing = runCatching { notifications.getAutomaticZenRule(id) }.getOrNull()
            // Rules from before "アラームのみ" became a priority mode are replaced.
            if (existing != null && existing.interruptionFilter == NotificationManager.INTERRUPTION_FILTER_PRIORITY) return id
            if (existing != null) runCatching { notifications.removeAutomaticZenRule(id) }
        }
        val name = app.getString(R.string.dnd_rule_name, app.getString(mode.label))
        val activity = ComponentName(app, MainActivity::class.java)
        val rule = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM) {
            AutomaticZenRule.Builder(name, conditionId(mode))
                .setType(AutomaticZenRule.TYPE_OTHER)
                .setConfigurationActivity(activity)
                .setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_PRIORITY)
                .setZenPolicy(mode.policy())
                .setIconResId(R.drawable.ic_profile_night)
                .setTriggerDescription(app.getString(R.string.dnd_rule_trigger))
                .build()
        } else {
            @Suppress("DEPRECATION")
            AutomaticZenRule(name, null, activity, conditionId(mode), mode.policy(), NotificationManager.INTERRUPTION_FILTER_PRIORITY, true)
        }
        val id = notifications.addAutomaticZenRule(rule)
        prefs.edit(commit = true) { putString(ruleKey(mode), id) }
        return id
    }

    private fun ruleKey(mode: DndMode) = "dnd_rule_${mode.key}"

    private fun conditionId(mode: DndMode): Uri =
        Uri.Builder().scheme(Condition.SCHEME).authority(app.packageName).appendPath(mode.key).build()

    private fun condition(mode: DndMode, on: Boolean, source: SwitchSource): Condition {
        if (on) prefs.edit(commit = true) { putString(KEY_LAST_ON, mode.key) }
        val state = if (on) Condition.STATE_TRUE else Condition.STATE_FALSE
        val summary = app.getString(mode.label)
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.VANILLA_ICE_CREAM) {
            return Condition(conditionId(mode), summary, state)
        }
        // A tap is the user's choice; a schedule or a timer's end is Volace's own doing.
        val origin = when (source) {
            SwitchSource.USER -> Condition.SOURCE_USER_ACTION
            SwitchSource.SCHEDULE -> Condition.SOURCE_SCHEDULE
            SwitchSource.CONTEXT -> Condition.SOURCE_CONTEXT
        }
        return Condition(conditionId(mode), summary, state, origin)
    }

    private companion object {
        /** Device-local: rule ids are this phone's (volace_device.xml is excluded from backups). */
        const val PREFS = "volace_device"
        const val KEY_SILENT = "dnd_silent_by_volace"
        const val KEY_LAST_ON = "dnd_last_on"
    }
}
