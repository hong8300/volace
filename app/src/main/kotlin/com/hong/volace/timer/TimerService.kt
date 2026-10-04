package com.hong.volace.timer

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.text.format.DateFormat
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import com.hong.volace.MainActivity
import com.hong.volace.R
import com.hong.volace.widget.WidgetRefresher
import com.hong.volace.widget.WidgetScope
import kotlinx.coroutines.launch
import java.util.Calendar
import java.util.Date

private const val TAG = "VolaceTimer"

/**
 * Runs from the start of a timed profile to its end, showing it as an ongoing notification, and
 * ends it: Android 17 lets the app change volumes from the background only while a foreground
 * service runs (DESIGN.md 5.14, 8.7). The alarm at the end ([TimerReceiver]) sends [ACTION_EXPIRE]
 * here, which also starts the service again when it is gone (after a reboot, or killed).
 */
class TimerService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val timer = TimerStore.load(this)
        // Required after startForegroundService(), even when there turns out to be nothing to show.
        val notification = TimerNotifications.running(this, timer)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(TimerNotifications.RUNNING_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            // API 33 has no "special use" type; the manifest's is used.
            startForeground(TimerNotifications.RUNNING_ID, notification)
        }
        if (intent?.action == ACTION_EXPIRE) {
            val app = applicationContext
            // Not tied to the service's lifetime: expire() stops it before the redraw.
            WidgetScope.run(null) {
                ProfileTimers.expire(app)
                WidgetRefresher.refreshAll(app)
                // A stale alarm (the timer was extended) leaves the timer running, and the service with it.
                if (ProfileTimers.current(app) == null) stop(app)
            }
            return START_NOT_STICKY
        }
        if (timer == null || timer.isDue(System.currentTimeMillis())) {
            stopSelf()
            return START_NOT_STICKY
        }
        // If the process is killed, come back and keep the notification up until the end.
        return START_STICKY
    }

    companion object {
        private const val ACTION_EXPIRE = "com.hong.volace.action.TIMER_SERVICE_EXPIRE"

        /** The alarm at the end: ends the timer inside the service. False when it could not start. */
        fun expire(context: Context): Boolean =
            runCatching {
                context.startForegroundService(Intent(context, TimerService::class.java).setAction(ACTION_EXPIRE))
            }.onFailure { Log.w(TAG, "could not start the timer service to end the timer", it) }.isSuccess

        fun start(context: Context) {
            // Not allowed from some background states. Then only the notification is missing: the
            // alarm at the end starts the service by itself (expire).
            runCatching { context.startForegroundService(Intent(context, TimerService::class.java)) }
                .onFailure { Log.w(TAG, "could not start the timer service", it) }
        }

        /** Shows the new end time. */
        fun refresh(context: Context) {
            val timer = TimerStore.load(context) ?: return
            context.getSystemService(NotificationManager::class.java)
                ?.notify(TimerNotifications.RUNNING_ID, TimerNotifications.running(context, timer))
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, TimerService::class.java))
        }
    }
}

/** "15:00" today, "明日 7:00" when the end is on another day. */
fun timerEndText(context: Context, endAt: Long, now: Long = System.currentTimeMillis()): String {
    val time = DateFormat.getTimeFormat(context).format(Date(endAt))
    val end = Calendar.getInstance().apply { timeInMillis = endAt }
    val today = Calendar.getInstance().apply { timeInMillis = now }
    val sameDay = end.get(Calendar.YEAR) == today.get(Calendar.YEAR) &&
        end.get(Calendar.DAY_OF_YEAR) == today.get(Calendar.DAY_OF_YEAR)
    return if (sameDay) time else context.getString(R.string.timer_tomorrow, time)
}

internal object TimerNotifications {

    const val RUNNING_ID = 1001
    private const val DUE_ID = 1002
    private const val CHANNEL_RUNNING = "timer"
    private const val CHANNEL_DUE = "timer_due"

    private fun manager(context: Context) = context.getSystemService(NotificationManager::class.java)

    private fun ensureChannels(context: Context) {
        manager(context)?.createNotificationChannels(
            listOf(
                NotificationChannel(
                    CHANNEL_RUNNING,
                    context.getString(R.string.timer_channel_running),
                    NotificationManager.IMPORTANCE_LOW,
                ),
                NotificationChannel(
                    CHANNEL_DUE,
                    context.getString(R.string.timer_channel_due),
                    NotificationManager.IMPORTANCE_HIGH,
                ),
            ),
        )
    }

    /** The ongoing notification: what runs, until when, and the two things one may want. */
    fun running(context: Context, timer: ProfileTimer?): Notification {
        ensureChannels(context)
        val builder = Notification.Builder(context, CHANNEL_RUNNING)
            .setSmallIcon(R.drawable.ic_timer)
            .setOngoing(true)
            .setCategory(Notification.CATEGORY_STATUS)
            .setContentIntent(openApp(context))
        if (timer == null) return builder.setContentTitle(context.getString(R.string.app_name)).build()
        return builder
            .setContentTitle(context.getString(R.string.timer_running_title, timer.profileName))
            .setContentText(
                context.getString(R.string.timer_running_text, timerEndText(context, timer.endAt), timer.restoreName),
            )
            // A countdown in the header.
            .setWhen(timer.endAt)
            .setShowWhen(true)
            .setUsesChronometer(true)
            .setChronometerCountDown(true)
            .addAction(
                Notification.Action.Builder(null, context.getString(R.string.timer_restore_now), restoreNow(context)).build(),
            )
            .addAction(
                Notification.Action.Builder(null, context.getString(R.string.timer_extend), extend(context)).build(),
            )
            .build()
    }

    /** The end came but Android refused to go back: a tap (a user action) is allowed to. */
    fun showDue(context: Context, restoreName: String) {
        ensureChannels(context)
        val notification = Notification.Builder(context, CHANNEL_DUE)
            .setSmallIcon(R.drawable.ic_timer)
            .setContentTitle(context.getString(R.string.timer_due_title))
            .setContentText(context.getString(R.string.timer_due_text, restoreName))
            .setCategory(Notification.CATEGORY_REMINDER)
            .setContentIntent(restoreNow(context))
            .setAutoCancel(true)
            .build()
        // Without the notification permission this shows nothing; the list still offers the button.
        runCatching { manager(context)?.notify(DUE_ID, notification) }
    }

    fun cancelDue(context: Context) {
        manager(context)?.cancel(DUE_ID)
    }

    private fun openApp(context: Context): PendingIntent = PendingIntent.getActivity(
        context, 0, MainActivity.launcherIntent(context), PendingIntent.FLAG_IMMUTABLE,
    )

    // An activity, not a broadcast: a visible activity may always change the volume.
    private fun restoreNow(context: Context): PendingIntent = PendingIntent.getActivity(
        context,
        0,
        Intent(context, TimerActionActivity::class.java)
            .setAction(TimerActionActivity.ACTION_RESTORE)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        PendingIntent.FLAG_IMMUTABLE,
    )

    private fun extend(context: Context): PendingIntent = PendingIntent.getBroadcast(
        context,
        1,
        Intent(context, TimerReceiver::class.java).setAction(TimerReceiver.ACTION_EXTEND),
        PendingIntent.FLAG_IMMUTABLE,
    )
}

/** The alarm at the end, and "延長" from the notification. */
class TimerReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext
        val action = intent.action
        if (action != ACTION_EXPIRE && action != ACTION_EXTEND) return
        // Going back is done in the service (an exact alarm may start it from the background).
        if (action == ACTION_EXPIRE && TimerService.expire(app)) return
        WidgetScope.run(goAsync()) {
            if (action == ACTION_EXPIRE) ProfileTimers.expire(app) else ProfileTimers.extend(app)
            WidgetRefresher.refreshAll(app)
        }
    }

    companion object {
        const val ACTION_EXPIRE = "com.hong.volace.action.TIMER_EXPIRE"
        const val ACTION_EXTEND = "com.hong.volace.action.TIMER_EXTEND"
    }
}

/** Alarms and services do not survive a reboot or an app update; picks the timer up again. */
class TimerBootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        if (action != Intent.ACTION_BOOT_COMPLETED && action != Intent.ACTION_MY_PACKAGE_REPLACED) return
        val app = context.applicationContext
        WidgetScope.run(goAsync()) {
            if (ProfileTimers.current(app) == null) return@run
            ProfileTimers.resume(app)
            WidgetRefresher.refreshAll(app)
        }
    }
}

/** "今すぐ戻す" from a notification: goes back from a (transparent) visible activity, then closes. */
class TimerActionActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (intent.action != ACTION_RESTORE) {
            finish()
            return
        }
        lifecycleScope.launch {
            val outcome = ProfileTimers.restore(this@TimerActionActivity)
            Toast.makeText(
                this@TimerActionActivity,
                outcome?.message(this@TimerActionActivity) ?: getString(R.string.timer_none),
                Toast.LENGTH_SHORT,
            ).show()
            WidgetRefresher.request(this@TimerActionActivity)
            finish()
        }
    }

    companion object {
        const val ACTION_RESTORE = "com.hong.volace.action.TIMER_RESTORE"
    }
}
