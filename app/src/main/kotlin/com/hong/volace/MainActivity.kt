package com.hong.volace

import android.app.NotificationManager
import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.lifecycleScope
import com.hong.volace.audio.VolumeApplier
import com.hong.volace.data.DefaultProfiles
import com.hong.volace.data.VolaceDatabase
import com.hong.volace.ui.edit.ProfileEditScreen
import com.hong.volace.ui.list.ProfileListScreen
import com.hong.volace.ui.onboarding.OnboardingScreen
import com.hong.volace.ui.theme.VolaceTheme
import com.hong.volace.widget.WidgetRefresher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

sealed interface Screen {
    data object ProfileList : Screen
    data class ProfileEdit(val profileId: Long?) : Screen
}

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
        }

        setContent {
            VolaceTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    val dndGranted by dndGrantedState
                    var screen by remember { mutableStateOf<Screen>(Screen.ProfileList) }
                    var message by remember { mutableStateOf<String?>(null) }

                    if (!dndGranted) {
                        OnboardingScreen()
                    } else {
                        when (val current = screen) {
                            Screen.ProfileList -> ProfileListScreen(
                                dao = db.profileDao(),
                                volumeApplier = volumeApplier,
                                onEditProfile = { id -> screen = Screen.ProfileEdit(id) },
                                onAddProfile = { screen = Screen.ProfileEdit(null) },
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
                                },
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

    private companion object {
        const val PREFS = "volace"
        const val KEY_SEEDED = "defaults_seeded"
    }

    private fun checkDndAccess(): Boolean {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        return nm.isNotificationPolicyAccessGranted
    }
}
