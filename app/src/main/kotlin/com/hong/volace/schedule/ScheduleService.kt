package com.hong.volace.schedule

import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import com.hong.volace.R
import com.hong.volace.audio.ProfileSwitcher
import com.hong.volace.audio.message
import com.hong.volace.widget.WidgetRefresher
import com.hong.volace.widget.WidgetScope
import kotlinx.coroutines.launch

private const val TAG = "VolaceSchedule"

/**
 * Runs for the second it takes to switch at a boundary. Started by the exact alarm, which may
 * start a foreground service from the background; while it runs, Android 17 lets the app change
 * volumes (DESIGN.md 8.7: the app targets API 36, so no "while-in-use" capability is needed).
 */
class ScheduleService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val notification = ScheduleNotifications.running(this)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(ScheduleNotifications.RUNNING_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            // API 33 has no "special use" type; the manifest's is used.
            startForeground(ScheduleNotifications.RUNNING_ID, notification)
        }
        val app = applicationContext
        // Not tied to the service's lifetime: stopping it must not cut the redraw short.
        WidgetScope.run(null) {
            try {
                Schedules.run(app)
            } finally {
                stopSelf(startId)
            }
        }
        return START_NOT_STICKY
    }

    companion object {
        /** False when Android refused to start it. */
        fun start(context: Context): Boolean =
            runCatching { context.startForegroundService(Intent(context, ScheduleService::class.java)) }
                .onFailure { Log.w(TAG, "could not start the schedule service", it) }
                .isSuccess
    }
}

/** The alarm at a boundary. */
class ScheduleReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_BOUNDARY) return
        val app = context.applicationContext
        if (ScheduleService.start(app)) return
        // Switching from here is likely ignored on Android 17, but is still read back and then
        // falls back to the "tap to switch" notification; the next alarm gets armed either way.
        WidgetScope.run(goAsync()) { Schedules.run(app) }
    }

    companion object {
        const val ACTION_BOUNDARY = "com.hong.volace.action.SCHEDULE_BOUNDARY"
    }
}

/** Alarms do not survive a reboot or an app update, and a clock or time-zone change moves the boundaries. */
class ScheduleBootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED,
            -> Unit
            else -> return
        }
        val app = context.applicationContext
        WidgetScope.run(goAsync()) { Schedules.reschedule(app) }
    }
}

/** "Tap to switch" from the notification: switches from a (transparent) visible activity, then closes. */
class ScheduleActionActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val id = intent.getLongExtra(EXTRA_PROFILE_ID, -1L)
        lifecycleScope.launch {
            // A choice by hand: marks the failed switch as done (Schedules.noteManualChoice).
            val outcome = ProfileSwitcher.apply(this@ScheduleActionActivity, id)
            Toast.makeText(
                this@ScheduleActionActivity,
                outcome?.result?.message(this@ScheduleActionActivity, outcome.profile.name)
                    ?: getString(R.string.shortcut_profile_gone),
                Toast.LENGTH_SHORT,
            ).show()
            WidgetRefresher.request(this@ScheduleActionActivity)
            finish()
        }
    }

    companion object {
        const val EXTRA_PROFILE_ID = "com.hong.volace.extra.SCHEDULE_PROFILE_ID"
    }
}
