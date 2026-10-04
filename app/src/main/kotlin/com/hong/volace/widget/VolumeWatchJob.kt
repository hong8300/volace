package com.hong.volace.widget

import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.content.ComponentName
import android.content.Context
import android.provider.Settings
import android.util.Log

/**
 * Keeps the widgets' volume read-out current when something other than Volace changes the volume
 * (volume keys, the system panel, another app).
 *
 * AudioService persists every level to Settings.System (`volume_music_speaker`, ...) and the
 * ringer mode to Settings.Global, so a content-triggered job is woken for those writes even while
 * our process is dead — no foreground service or permanent notification needed. Such a job fires
 * once; [WidgetRefresher.refreshAll] re-arms it at the end of every redraw, and re-arming it while
 * it is still running carries over any change that arrived in the meantime.
 */
class VolumeWatchJob : JobService() {

    override fun onStartJob(params: JobParameters): Boolean {
        WidgetScope.run(null) {
            try {
                WidgetRefresher.refreshAll(applicationContext)
            } finally {
                // Usually already ended by the re-arm inside refreshAll; harmless then.
                jobFinished(params, false)
            }
        }
        return true
    }

    override fun onStopJob(params: JobParameters): Boolean = false

    companion object {
        private const val JOB_ID = 1

        /** Coalesce a held volume key into one redraw, but never lag by more than a second. */
        private const val UPDATE_DELAY_MS = 100L
        private const val MAX_DELAY_MS = 1_000L

        /** Watches while at least one widget is placed; [armed] = false stops watching. */
        fun sync(context: Context, armed: Boolean) {
            val scheduler = context.getSystemService(JobScheduler::class.java)
            if (!armed) {
                scheduler.cancel(JOB_ID)
                return
            }
            val job = JobInfo.Builder(JOB_ID, ComponentName(context, VolumeWatchJob::class.java))
                .addTriggerContentUri(
                    JobInfo.TriggerContentUri(
                        Settings.System.CONTENT_URI,
                        JobInfo.TriggerContentUri.FLAG_NOTIFY_FOR_DESCENDANTS,
                    ),
                )
                .addTriggerContentUri(
                    JobInfo.TriggerContentUri(Settings.Global.getUriFor(Settings.Global.MODE_RINGER), 0),
                )
                .setTriggerContentUpdateDelay(UPDATE_DELAY_MS)
                .setTriggerContentMaxDelay(MAX_DELAY_MS)
                .build()
            if (scheduler.schedule(job) != JobScheduler.RESULT_SUCCESS) {
                Log.w("VolumeWatchJob", "could not arm the volume watch; widgets update on the next tap")
            }
        }
    }
}
