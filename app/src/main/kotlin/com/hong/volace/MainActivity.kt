package com.hong.volace

import com.hong.volace.ui.bluetooth.BluetoothScreen
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.lifecycleScope
import com.hong.volace.audio.DndMode
import com.hong.volace.audio.DndModes
import com.hong.volace.audio.VolumeApplier
import com.hong.volace.data.DefaultProfiles
import com.hong.volace.data.VolaceDatabase
import com.hong.volace.schedule.Schedules
import com.hong.volace.ui.edit.ProfileEditScreen
import com.hong.volace.ui.list.ProfileListScreen
import com.hong.volace.ui.onboarding.OnboardingScreen
import com.hong.volace.ui.schedule.ScheduleScreen
import com.hong.volace.ui.theme.VolaceTheme
import com.hong.volace.widget.WidgetRefresher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

sealed interface Screen {
    data object ProfileList : Screen
    data class ProfileEdit(val profileId: Long?) : Screen
    data object Schedule : Screen
    data object Bluetooth : Screen
}

/** Keeps the open screen across rotation and process death. Ids are never negative. */
private val ScreenSaver = Saver<Screen, Long>(
    save = { screen ->
        when (screen) {
            Screen.ProfileList -> -1L
            is Screen.ProfileEdit -> screen.profileId ?: -2L
            Screen.Schedule -> -3L
            Screen.Bluetooth -> -4L
        }
    },
    restore = { saved ->
        when (saved) {
            -1L -> Screen.ProfileList
            -2L -> Screen.ProfileEdit(null)
            -3L -> Screen.Schedule
            -4L -> Screen.Bluetooth
            else -> Screen.ProfileEdit(saved)
        }
    },
)

class MainActivity : ComponentActivity() {
    private val dndGrantedState = mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val db = VolaceDatabase.get(this)
        val volumeApplier = VolumeApplier(this)
        dndGrantedState.value = checkDndAccess()

        lifecycleScope.launch(Dispatchers.IO) {
            // Seed the defaults once per install. Checking only for an empty table brought the
            // four defaults back on every launch after the user had deleted them all.
            val prefs = getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            if (!prefs.getBoolean(KEY_SEEDED, false)) {
                db.profileDao().insertIfEmpty(DefaultProfiles.build(volumeApplier))
                prefs.edit().putBoolean(KEY_SEEDED, true).apply()
                WidgetRefresher.refreshAll(applicationContext)
            }
            // The schedule's alarm is gone after a force stop, which no broadcast reports.
            Schedules.reschedule(applicationContext)
            removeUnusedDndModes()
        }

        setContent {
            VolaceTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    val dndGranted by dndGrantedState
                    var screen by rememberSaveable(stateSaver = ScreenSaver) {
                        mutableStateOf<Screen>(Screen.ProfileList)
                    }
                    var message by rememberSaveable { mutableStateOf<String?>(null) }

                    if (!dndGranted) {
                        OnboardingScreen()
                    } else {
                        when (val current = screen) {
                            Screen.ProfileList -> ProfileListScreen(
                                dao = db.profileDao(),
                                volumeApplier = volumeApplier,
                                onEditProfile = { id -> screen = Screen.ProfileEdit(id) },
                                onAddProfile = { screen = Screen.ProfileEdit(null) },
                                scheduleDao = db.scheduleDao(),
                                onOpenSchedule = { screen = Screen.Schedule },
                                bluetoothDao = db.bluetoothRuleDao(),
                                onOpenBluetooth = { screen = Screen.Bluetooth },
                                message = message,
                                onMessageShown = { message = null },
                            )
                            is Screen.ProfileEdit -> ProfileEditScreen(
                                profileId = current.profileId,
                                dao = db.profileDao(),
                                volumeApplier = volumeApplier,
                                onDone = { result ->
                                    message = result
                                    screen = Screen.ProfileList
                                    // A profile saved without its DND mode, or deleted.
                                    lifecycleScope.launch(Dispatchers.IO) { removeUnusedDndModes() }
                                },
                            )
                            Screen.Schedule -> ScheduleScreen(
                                profileDao = db.profileDao(),
                                scheduleDao = db.scheduleDao(),
                                onBack = { screen = Screen.ProfileList },
                            )
                            Screen.Bluetooth -> BluetoothScreen(
                                profileDao = db.profileDao(),
                                ruleDao = db.bluetoothRuleDao(),
                                onBack = { screen = Screen.ProfileList },
                            )
                        }
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        dndGrantedState.value = checkDndAccess()
    }

    override fun onPause() {
        super.onPause()
        // Anything changed in the app should be visible on the home screen straight away.
        WidgetRefresher.request(this)
    }

    companion object {
        private const val PREFS = "volace"
        private const val KEY_SEEDED = "defaults_seeded"

        /**
         * What the launcher sends: brings the existing task back as it was (e.g. a half-done
         * edit) or starts fresh on the list. Clearing to the list instead (CLEAR_TOP) silently
         * dropped an unsaved edit whenever the app was reopened from a widget.
         */
        fun launcherIntent(context: Context): Intent =
            Intent(Intent.ACTION_MAIN)
                .addCategory(Intent.CATEGORY_LAUNCHER)
                .setClass(context, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
    }

    /** Volace's DND modes that no profile uses leave the system's Modes list (DndModes.removeUnused). */
    private suspend fun removeUnusedDndModes() {
        if (!checkDndAccess()) return
        val used = VolaceDatabase.get(this).profileDao().getAllOnce().map { DndMode.of(it.dndMode) }.toSet()
        DndModes(this).removeUnused(used)
    }

    private fun checkDndAccess(): Boolean {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        return nm.isNotificationPolicyAccessGranted
    }
}
