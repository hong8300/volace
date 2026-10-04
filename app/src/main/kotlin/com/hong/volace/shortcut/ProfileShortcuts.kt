package com.hong.volace.shortcut

import android.graphics.Paint
import com.hong.volace.ui.theme.IconStyle
import com.hong.volace.ui.theme.SkinStore
import android.content.Context
import android.content.Intent
import android.content.pm.ShortcutInfo
import android.content.pm.ShortcutManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.drawable.Icon
import android.net.Uri
import com.hong.volace.R
import com.hong.volace.data.Profile
import com.hong.volace.data.VolaceDatabase
import com.hong.volace.data.icon
import com.hong.volace.data.contentColorOn

/**
 * Launcher shortcuts (long-press the app icon): the first profiles, each applied in one tap.
 * They can be dragged onto the home screen to pin them.
 *
 * ShortcutManager rate-limits apps that update shortcuts from the background, and redraws run
 * from the background all the time (every volume change), so the list is only pushed when what
 * the shortcuts show (ids, names, icons, colours, order) actually changed.
 */
object ProfileShortcuts {

    /** Device-local: excluded from backups, or a restored copy would skip publishing here. */
    private const val PREFS = "volace_device"
    private const val KEY_SIGNATURE = "shortcuts_signature"
    private const val ID_PREFIX = "profile-"

    suspend fun sync(context: Context) {
        val app = context.applicationContext
        val manager = app.getSystemService(ShortcutManager::class.java) ?: return
        val profiles = VolaceDatabase.get(app).profileDao().getAllOnce()
        // Launchers show about four; more would just be cut.
        val shown = profiles.take(minOf(4, manager.maxShortcutCountPerActivity))

        val emoji = SkinStore.iconStyle(context) == IconStyle.EMOJI
        val signature = shown.joinToString("|") { "${it.id}:${it.name}:${it.iconKey}:${it.colorArgb}" } +
            if (emoji) "|emoji" else ""
        val prefs = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (prefs.getString(KEY_SIGNATURE, null) == signature) return

        // False when rate-limited: leave the signature alone so the next pass tries again.
        if (!manager.setDynamicShortcuts(shown.mapIndexed { rank, p -> shortcut(app, p, rank) })) return
        // A pinned shortcut to a deleted profile stays on the home screen; grey it out.
        val alive = profiles.map { ID_PREFIX + it.id }.toSet()
        val gone = manager.pinnedShortcuts.map { it.id }.filter { it.startsWith(ID_PREFIX) && it !in alive }
        if (gone.isNotEmpty()) manager.disableShortcuts(gone, app.getString(R.string.shortcut_deleted))
        // Pinned ones that still exist pick up a new name / icon too.
        val pinnedAlive = manager.pinnedShortcuts.map { it.id }.filter { it in alive }.toSet()
        val updates = profiles.filter { ID_PREFIX + it.id in pinnedAlive }.map { shortcut(app, it, 0) }
        if (updates.isNotEmpty()) manager.updateShortcuts(updates)

        prefs.edit().putString(KEY_SIGNATURE, signature).apply()
    }

    private fun shortcut(context: Context, profile: Profile, rank: Int): ShortcutInfo =
        ShortcutInfo.Builder(context, ID_PREFIX + profile.id)
            .setShortLabel(profile.name)
            .setLongLabel(context.getString(R.string.shortcut_apply, profile.name))
            .setIcon(icon(context, profile))
            .setRank(rank)
            .setIntent(
                Intent(context, ApplyShortcutActivity::class.java)
                    .setAction(Intent.ACTION_VIEW)
                    // Distinct data so each shortcut keeps its own intent.
                    .setData(Uri.parse("volace://apply/${profile.id}"))
                    .putExtra(ApplyShortcutActivity.EXTRA_PROFILE_ID, profile.id),
            )
            .build()

    /** The profile's icon in white on its own colour, as an adaptive icon like the app's. */
    private fun icon(context: Context, profile: Profile): Icon {
        val density = context.resources.displayMetrics.density
        val size = (108 * density).toInt() // adaptive icon canvas
        val glyph = (44 * density).toInt() // well inside the 66dp safe zone
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(profile.colorArgb)
        if (SkinStore.iconStyle(context) == IconStyle.EMOJI) {
            // The "かわいい" icon style: the emoji in its own colours.
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                textSize = glyph.toFloat()
                textAlign = Paint.Align.CENTER
            }
            val baseline = size / 2f - (paint.descent() + paint.ascent()) / 2f
            canvas.drawText(profile.icon.emoji, size / 2f, baseline, paint)
            return Icon.createWithAdaptiveBitmap(bitmap)
        }
        context.getDrawable(profile.icon.res)?.mutate()?.apply {
            colorFilter = PorterDuffColorFilter(contentColorOn(profile.colorArgb), PorterDuff.Mode.SRC_IN)
            val inset = (size - glyph) / 2
            setBounds(inset, inset, inset + glyph, inset + glyph)
            draw(canvas)
        }
        return Icon.createWithAdaptiveBitmap(bitmap)
    }
}
