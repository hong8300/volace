package com.hong.volace.bluetooth

import android.annotation.SuppressLint
import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.media.AudioAttributes
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.util.Log
import androidx.core.content.edit
import com.hong.volace.MainActivity
import com.hong.volace.R
import com.hong.volace.audio.ApplyResult
import com.hong.volace.audio.DeviceVolumes
import com.hong.volace.audio.ProfileSwitcher
import com.hong.volace.audio.SoundKind
import com.hong.volace.audio.Sounds
import com.hong.volace.audio.SwitchSource
import com.hong.volace.audio.VolumeApplier
import com.hong.volace.audio.VolumeStream
import com.hong.volace.audio.copyWith
import com.hong.volace.audio.keepIn
import com.hong.volace.audio.valueOf
import com.hong.volace.data.BluetoothRule
import com.hong.volace.data.Profile
import com.hong.volace.data.VolaceDatabase
import com.hong.volace.shortcut.ApplyShortcutActivity
import com.hong.volace.timer.ProfileTimers
import com.hong.volace.timer.TimerStore
import com.hong.volace.timer.asProfile
import com.hong.volace.widget.WidgetRefresher
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONArray
import org.json.JSONObject

private const val TAG = "VolaceBluetooth"

/**
 * Switching profiles when a Bluetooth device connects or disconnects (DESIGN.md 5.18).
 *
 * Android 17 lets an app change volumes from the background only while a foreground service runs,
 * and a Bluetooth broadcast may not start one. So the broadcast ([BluetoothReceiver]) only notes
 * the event and sets an exact alarm due now; the alarm may start [BluetoothService], which does
 * the switch (as the schedule does, 5.15). Each switch happens once, at the event: a profile
 * chosen by hand while connected stays, and is not undone at the disconnect.
 */
object BluetoothSwitch {

    /** How long to wait for media to move to the device (the audio route follows the link). */
    private const val ROUTE_TIMEOUT_MS = 8_000L

    /** A headset may report its own volume right after connecting; wait this long, then try again. */
    private const val RETRY_DELAY_MS = 1_500L

    private val drain = Mutex()

    fun observeStatus(context: Context): Flow<BluetoothStatus?> = BluetoothStore.observeStatus(context)

    /**
     * A profile was chosen by hand (the "tap to switch" notification included): a failed switch is
     * no longer pending (as Schedules.noteManualChoice).
     */
    internal fun noteManualChoice(context: Context, profileId: Long) {
        val status = BluetoothStore.status(context) ?: return
        if (!status.outcome.failed || status.outcome == BluetoothStatus.Outcome.PROFILE_GONE) return
        val outcome = if (status.profileId == profileId) BluetoothStatus.Outcome.BY_HAND else BluetoothStatus.Outcome.OVERRIDDEN
        BluetoothStore.saveStatus(context, status.copy(outcome = outcome))
        BluetoothNotifications.cancelFailed(context)
    }

    /** From the broadcast: notes the event for a device with a rule; the alarm does the rest. */
    suspend fun onEvent(context: Context, address: String, connected: Boolean) {
        val app = context.applicationContext
        val rule = VolaceDatabase.get(app).bluetoothRuleDao().forAddress(address) ?: return
        if (!rule.enabled) return
        BluetoothStore.enqueue(app, PendingEvent(address, connected))
        BluetoothAlarm.setNow(app)
    }

    /** In [BluetoothService] (or the alarm's receiver if that could not start): every noted event, in order. */
    suspend fun process(context: Context) = drain.withLock {
        val app = context.applicationContext
        val dao = VolaceDatabase.get(app).bluetoothRuleDao()
        while (true) {
            val event = BluetoothStore.poll(app) ?: break
            val rule = dao.forAddress(event.address)?.takeIf { it.enabled } ?: continue
            val status = if (event.connected) connected(app, rule) else disconnected(app, rule)
            if (status == null) continue
            BluetoothStore.saveStatus(app, status)
            if (status.outcome.failed) {
                Log.w(TAG, "${rule.name}: ${status.outcome}")
                BluetoothNotifications.showFailed(app, status)
            } else {
                BluetoothNotifications.cancelFailed(app)
            }
        }
        WidgetRefresher.refreshAll(app)
    }

    private suspend fun connected(app: Context, rule: BluetoothRule): BluetoothStatus {
        // Media plays on the device only once its audio route is up, and its volume is its own.
        awaitRoute(app, rule.address)
        return ProfileSwitcher.exclusive(app) { _, profiles ->
            val profile = profiles.getById(rule.profileId)
                ?: return@exclusive BluetoothStatus(rule.name, "", connected = true, BluetoothStatus.Outcome.PROFILE_GONE)
            fun status(outcome: BluetoothStatus.Outcome) =
                BluetoothStatus(rule.name, profile.name, connected = true, outcome, profileId = profile.id)

            // A timed profile was chosen by hand: it runs to its end and then becomes this one.
            val timer = TimerStore.load(app)
            if (timer != null && !timer.isDue(System.currentTimeMillis())) {
                ProfileTimers.handOverLocked(app, profile)
                return@exclusive status(BluetoothStatus.Outcome.AFTER_TIMER)
            }

            val applier = VolumeApplier(app)
            val before = applier.snapshot()
            val previousActiveId = profiles.getAllOnce().firstOrNull { it.isActive }?.id
            val previousSounds = SoundKind.entries.filter { it.valueOf(profile) != null }.associateWith { Sounds.current(app, it) }
            val result = applyAndCheck(applier, profile)
            if (result == ApplyResult.Applied) {
                profiles.applyActive(profile.id)
                ProfileTimers.dropLocked(app)
                BluetoothStore.saveSession(app, rule.address, Session(profile.id, before, previousActiveId, previousSounds))
            }
            status(outcomeOf(result, BluetoothStatus.Outcome.APPLIED))
        }
    }

    private suspend fun disconnected(app: Context, rule: BluetoothRule): BluetoothStatus? =
        ProfileSwitcher.exclusive(app) { _, profiles ->
            val session = BluetoothStore.session(app, rule.address) ?: return@exclusive null
            BluetoothStore.clearSession(app, rule.address)
            val connectedName = profiles.getById(session.profileId)?.name.orEmpty()
            // Something else was chosen while connected (by hand, a schedule, a timer): it stays.
            if (profiles.getAllOnce().firstOrNull { it.isActive }?.id != session.profileId) {
                return@exclusive BluetoothStatus(rule.name, connectedName, connected = false, BluetoothStatus.Outcome.KEPT)
            }
            if (rule.onDisconnect == BluetoothRule.DISCONNECT_NOTHING) return@exclusive null

            val chosen = rule.disconnectProfileId?.takeIf { rule.onDisconnect == BluetoothRule.DISCONNECT_PROFILE }
                ?.let { profiles.getById(it) }
            val target = chosen ?: session.restoreProfile(app.getString(R.string.bt_previous_state))
            val result = applyAndCheck(VolumeApplier(app), target)
            if (result == ApplyResult.Applied) {
                when {
                    chosen != null -> profiles.applyActive(chosen.id)
                    session.previousActiveId != null -> profiles.applyActive(session.previousActiveId)
                    else -> profiles.clearActive()
                }
            }
            BluetoothStatus(rule.name, target.name, connected = false, outcomeOf(result, BluetoothStatus.Outcome.RESTORED))
        }

    /**
     * Applies and reads it back (Android 17 ignores what it does not allow without saying so).
     * Tried a second time: a headset may push its own volume just after connecting.
     */
    private suspend fun applyAndCheck(applier: VolumeApplier, profile: Profile): ApplyResult {
        repeat(2) { attempt ->
            val result = applier.apply(profile, SwitchSource.CONTEXT)
            if (result != ApplyResult.Applied) return result
            if (applier.matches(profile, applier.snapshot())) return result
            if (attempt == 0) delay(RETRY_DELAY_MS)
        }
        return ApplyResult.Partial(emptyList())
    }

    private fun outcomeOf(result: ApplyResult, success: BluetoothStatus.Outcome) = when (result) {
        ApplyResult.Applied -> success
        ApplyResult.NeedsAccess -> BluetoothStatus.Outcome.NEEDS_ACCESS
        is ApplyResult.Partial -> BluetoothStatus.Outcome.FAILED
    }

    private suspend fun awaitRoute(app: Context, address: String) {
        val audio = app.getSystemService(AudioManager::class.java)
        val media = AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).build()
        val deadline = System.currentTimeMillis() + ROUTE_TIMEOUT_MS
        while (System.currentTimeMillis() < deadline) {
            val routed = runCatching { audio.getAudioDevicesForAttributes(media) }.getOrDefault(emptyList())
            if (routed.any { it.type in BLUETOOTH_OUTPUTS && it.address.equals(address, ignoreCase = true) }) return
            delay(250)
        }
        // A device without media (a watch, a car kit on calls only): switch anyway.
        Log.i(TAG, "media did not move to $address; switching anyway")
    }

    private val BLUETOOTH_OUTPUTS = setOf(
        AudioDeviceInfo.TYPE_BLUETOOTH_A2DP,
        AudioDeviceInfo.TYPE_BLUETOOTH_SCO,
        AudioDeviceInfo.TYPE_BLE_HEADSET,
        AudioDeviceInfo.TYPE_BLE_SPEAKER,
        AudioDeviceInfo.TYPE_HEARING_AID,
    )
}

internal data class PendingEvent(val address: String, val connected: Boolean)

/**
 * What a device's connection changed, to undo at its disconnect: the state from before (as the
 * timer keeps it, ProfileTimer) and the profile it switched to.
 */
internal data class Session(
    val profileId: Long,
    val previous: DeviceVolumes,
    val previousActiveId: Long?,
    val previousSounds: Map<SoundKind, String>,
) {
    /**
     * The state from before, except media and call: Android keeps their volume per device, so
     * the device's own levels were the ones changed, and the speaker's come back by themselves.
     */
    fun restoreProfile(name: String): Profile {
        val withSounds = previousSounds.entries.fold(previous.asProfile(name)) { p, (kind, value) -> kind.copyWith(p, value) }
        return listOf(VolumeStream.MEDIA, VolumeStream.VOICE_CALL).fold(withSounds) { p, stream -> stream.keepIn(p, keep = true) }
    }

    fun toJson(): String = JSONObject()
        .put("profileId", profileId)
        .put("previousActiveId", previousActiveId ?: JSONObject.NULL)
        .put("ringerMode", previous.ringerMode)
        .put("dnd", previous.volaceDnd)
        .put("levels", JSONObject().apply { previous.levels.forEach { (stream, level) -> put(stream.key, level) } })
        .put("sounds", JSONObject().apply { previousSounds.forEach { (kind, value) -> put(kind.key, value) } })
        .toString()

    companion object {
        fun fromJson(text: String): Session? = runCatching {
            val json = JSONObject(text)
            val levels = json.getJSONObject("levels")
            val sounds = json.optJSONObject("sounds")
            Session(
                profileId = json.getLong("profileId"),
                previous = DeviceVolumes(
                    ringerMode = json.getInt("ringerMode"),
                    levels = VolumeStream.entries.associateWith { levels.optInt(it.key, 0) },
                    volaceDnd = json.optInt("dnd", 0),
                ),
                previousActiveId = if (json.isNull("previousActiveId")) null else json.getLong("previousActiveId"),
                previousSounds = SoundKind.entries.filter { sounds?.has(it.key) == true }.associateWith { sounds!!.getString(it.key) },
            )
        }.getOrNull()
    }
}

/** What the last connection or disconnection did, for the screens. */
data class BluetoothStatus(
    val deviceName: String,
    /** The profile switched to (or kept, for [Outcome.KEPT]); empty when it was gone. */
    val profileName: String,
    val connected: Boolean,
    val outcome: Outcome,
    val at: Long = System.currentTimeMillis(),
    /** The profile a connection switched to, for "tap to switch" when that failed. */
    val profileId: Long? = null,
) {
    enum class Outcome(val failed: Boolean) {
        APPLIED(false),

        /** Back to the state from before (or the disconnect profile). */
        RESTORED(false),

        /** A timed profile was running; it switches to this one when it ends. */
        AFTER_TIMER(false),

        /** Another profile was chosen while connected: left in effect. */
        KEPT(false),

        /** Failed, then switched to by hand (e.g. from the "tap to switch" notification). */
        BY_HAND(false),

        /** Failed, then another profile was chosen by hand, which has priority. */
        OVERRIDDEN(false),
        FAILED(true),
        NEEDS_ACCESS(true),
        PROFILE_GONE(true),
    }

    fun toJson(): String = JSONObject()
        .put("deviceName", deviceName)
        .put("profileName", profileName)
        .put("connected", connected)
        .put("outcome", outcome.name)
        .put("at", at)
        .put("profileId", profileId ?: JSONObject.NULL)
        .toString()

    companion object {
        fun fromJson(text: String): BluetoothStatus? = runCatching {
            val json = JSONObject(text)
            BluetoothStatus(
                deviceName = json.getString("deviceName"),
                profileName = json.optString("profileName"),
                connected = json.getBoolean("connected"),
                outcome = Outcome.valueOf(json.getString("outcome")),
                at = json.getLong("at"),
                profileId = if (json.isNull("profileId")) null else json.getLong("profileId"),
            )
        }.getOrNull()
    }
}

/** Device-local state (volace_device.xml is excluded from backups). */
internal object BluetoothStore {

    private const val PREFS = "volace_device"
    private const val KEY_QUEUE = "bt_queue"
    private const val KEY_STATUS = "bt_status"
    private fun sessionKey(address: String) = "bt_session_$address"

    private val lock = Any()

    private fun prefs(context: Context): SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun enqueue(context: Context, event: PendingEvent) = synchronized(lock) {
        val queue = JSONArray(prefs(context).getString(KEY_QUEUE, "[]"))
        queue.put(JSONObject().put("address", event.address).put("connected", event.connected))
        prefs(context).edit(commit = true) { putString(KEY_QUEUE, queue.toString()) }
    }

    fun poll(context: Context): PendingEvent? = synchronized(lock) {
        val queue = runCatching { JSONArray(prefs(context).getString(KEY_QUEUE, "[]")) }.getOrDefault(JSONArray())
        if (queue.length() == 0) return null
        val first = queue.getJSONObject(0)
        queue.remove(0)
        prefs(context).edit(commit = true) { putString(KEY_QUEUE, queue.toString()) }
        PendingEvent(first.getString("address"), first.getBoolean("connected"))
    }

    fun session(context: Context, address: String): Session? =
        prefs(context).getString(sessionKey(address), null)?.let(Session::fromJson)

    fun saveSession(context: Context, address: String, session: Session) {
        prefs(context).edit(commit = true) { putString(sessionKey(address), session.toJson()) }
    }

    fun clearSession(context: Context, address: String) {
        prefs(context).edit(commit = true) { remove(sessionKey(address)) }
    }

    fun status(context: Context): BluetoothStatus? =
        prefs(context).getString(KEY_STATUS, null)?.let(BluetoothStatus::fromJson)

    fun saveStatus(context: Context, status: BluetoothStatus) {
        prefs(context).edit(commit = true) { putString(KEY_STATUS, status.toJson()) }
    }

    fun observeStatus(context: Context): Flow<BluetoothStatus?> = callbackFlow {
        val prefs = prefs(context)
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == KEY_STATUS) trySend(status(context))
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        trySend(status(context))
        awaitClose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }
}

/** An exact alarm due now: it may start the service, which the Bluetooth broadcast may not. */
internal object BluetoothAlarm {

    @SuppressLint("MissingPermission")
    fun setNow(context: Context) {
        val alarms = context.getSystemService(AlarmManager::class.java) ?: return
        val now = System.currentTimeMillis()
        // USE_EXACT_ALARM is granted at install (lint only knows SCHEDULE_EXACT_ALARM).
        if (alarms.canScheduleExactAlarms()) {
            alarms.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, now, pendingIntent(context))
        } else {
            alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, now, pendingIntent(context))
        }
    }

    private fun pendingIntent(context: Context): PendingIntent = PendingIntent.getBroadcast(
        context,
        0,
        Intent(context, BluetoothAlarmReceiver::class.java).setAction(BluetoothAlarmReceiver.ACTION_PROCESS),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )
}

internal object BluetoothNotifications {

    const val RUNNING_ID = 1201
    private const val FAILED_ID = 1202
    private const val CHANNEL_RUNNING = "bluetooth"
    private const val CHANNEL_FAILED = "bluetooth_failed"

    private fun manager(context: Context) = context.getSystemService(NotificationManager::class.java)

    private fun ensureChannels(context: Context) {
        manager(context)?.createNotificationChannels(
            listOf(
                NotificationChannel(CHANNEL_RUNNING, context.getString(R.string.bt_channel_running), NotificationManager.IMPORTANCE_LOW),
                NotificationChannel(CHANNEL_FAILED, context.getString(R.string.bt_channel_failed), NotificationManager.IMPORTANCE_HIGH),
            ),
        )
    }

    /** For the second or so the service runs (Android usually does not get to show it). */
    fun running(context: Context): Notification {
        ensureChannels(context)
        return Notification.Builder(context, CHANNEL_RUNNING)
            .setSmallIcon(R.drawable.ic_bluetooth)
            .setContentTitle(context.getString(R.string.bt_running))
            .setCategory(Notification.CATEGORY_STATUS)
            .build()
    }

    fun showFailed(context: Context, status: BluetoothStatus) {
        ensureChannels(context)
        // Tapping switches from a visible activity, which may always change the volume; a failed
        // restore, or a gone profile, opens the app to choose.
        val profileId = status.profileId?.takeIf { status.connected && status.outcome == BluetoothStatus.Outcome.FAILED }
        val text = when (status.outcome) {
            BluetoothStatus.Outcome.NEEDS_ACCESS -> context.getString(R.string.schedule_failed_access)
            BluetoothStatus.Outcome.PROFILE_GONE -> context.getString(R.string.bt_failed_gone)
            else -> context.getString(if (profileId != null) R.string.bt_failed_text else R.string.bt_failed_text_open)
        }
        val notification = Notification.Builder(context, CHANNEL_FAILED)
            .setSmallIcon(R.drawable.ic_bluetooth)
            .setContentTitle(
                context.getString(
                    if (status.connected) R.string.bt_failed_title_connected else R.string.bt_failed_title_disconnected,
                    status.deviceName,
                    status.profileName,
                ),
            )
            .setContentText(text)
            .setCategory(Notification.CATEGORY_REMINDER)
            .setContentIntent(profileId?.let { switchTo(context, it) } ?: openApp(context))
            .setAutoCancel(true)
            .build()
        runCatching { manager(context)?.notify(FAILED_ID, notification) }
    }

    fun cancelFailed(context: Context) {
        manager(context)?.cancel(FAILED_ID)
    }

    private fun openApp(context: Context): PendingIntent = PendingIntent.getActivity(
        context, 0, MainActivity.launcherIntent(context), PendingIntent.FLAG_IMMUTABLE,
    )

    private fun switchTo(context: Context, profileId: Long): PendingIntent = PendingIntent.getActivity(
        context,
        3,
        Intent(context, ApplyShortcutActivity::class.java)
            .putExtra(ApplyShortcutActivity.EXTRA_PROFILE_ID, profileId)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )
}
