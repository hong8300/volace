package com.hong.volace.shortcut

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import com.hong.volace.R
import com.hong.volace.audio.ProfileSwitcher
import com.hong.volace.audio.message
import com.hong.volace.widget.WidgetRefresher
import kotlinx.coroutines.launch

/**
 * Target of a launcher shortcut: applies one profile and closes. A (transparent) activity rather
 * than a receiver because shortcuts can only start activities, and because Android 17 only lets a
 * visible activity change volumes (DESIGN.md 8.7).
 */
class ApplyShortcutActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val id = intent.getLongExtra(EXTRA_PROFILE_ID, -1L)
        lifecycleScope.launch {
            val outcome = ProfileSwitcher.apply(this@ApplyShortcutActivity, id)
            Toast.makeText(
                this@ApplyShortcutActivity,
                outcome?.result?.message(this@ApplyShortcutActivity, outcome.profile.name)
                    ?: getString(R.string.shortcut_profile_gone),
                Toast.LENGTH_SHORT,
            ).show()
            WidgetRefresher.request(this@ApplyShortcutActivity)
            finish()
        }
    }

    companion object {
        const val EXTRA_PROFILE_ID = "com.hong.volace.extra.PROFILE_ID"
    }
}
