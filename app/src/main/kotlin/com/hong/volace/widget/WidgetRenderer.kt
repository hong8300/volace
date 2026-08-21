package com.hong.volace.widget

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.view.View
import android.widget.RemoteViews
import com.hong.volace.MainActivity
import com.hong.volace.data.Profile
import com.hong.volace.data.ProfileIcon
import com.hong.volace.data.icon

/**
 * Builds the [RemoteViews] for a widget from the current profile list.
 *
 * Everything a cell shows is driven by remotable setters only ([RemoteViews.setInt] onto
 * `setColorFilter` / `setImageAlpha`), so a single pair of shape drawables can be recoloured per
 * profile without needing one drawable per colour.
 */
object WidgetRenderer {

    private const val ACTIVE_ALPHA = 255
    private const val IDLE_ALPHA = 48
    private const val RING_ALPHA = 225

    private val ACTIVE_TEXT = Color.WHITE
    private val IDLE_TEXT = 0xFFDCDCE6.toInt()
    private val EMPTY_TINT = 0xFF3B3B4A.toInt()

    fun build(context: Context, style: WidgetStyle, profiles: List<Profile>): RemoteViews {
        val views = RemoteViews(context.packageName, style.layout)
        if (profiles.isEmpty()) {
            renderEmpty(context, views)
            return views
        }
        if (style.cycles) renderCycle(context, views, profiles) else renderGrid(context, views, style, profiles)
        return views
    }

    /** One-cell widget: shows the profile in effect, tapping steps to the next one. */
    private fun renderCycle(context: Context, views: RemoteViews, profiles: List<Profile>) {
        val current = profiles.firstOrNull { it.isActive } ?: profiles.first()
        val cell = CELLS[0]
        paintCell(views, cell, current, active = current.isActive)
        views.setOnClickPendingIntent(cell.root, cyclePendingIntent(context))
        views.setContentDescription(cell.root, "現在: ${current.name}。タップで次のプロファイル")
    }

    private fun renderGrid(
        context: Context,
        views: RemoteViews,
        style: WidgetStyle,
        profiles: List<Profile>,
    ) {
        val shown = profiles.take(style.cellCount)

        // With a second row, keep empty cells INVISIBLE so buttons keep a sane size; on a single
        // row, GONE lets the remaining buttons grow and fill the bar.
        val hasSecondRow = style.secondRow != null
        val perRow = if (hasSecondRow) style.cellCount / 2 else style.cellCount
        val blankMode = if (hasSecondRow) View.INVISIBLE else View.GONE

        if (style.secondRow != null) {
            views.setViewVisibility(style.secondRow, if (shown.size > perRow) View.VISIBLE else View.GONE)
        }

        for (index in 0 until style.cellCount) {
            val cell = CELLS[index]
            val profile = shown.getOrNull(index)
            if (profile == null) {
                // Trailing blanks on the first row of a grid should collapse only when the whole
                // second row is gone; otherwise columns would stop lining up.
                val collapse = !hasSecondRow || shown.size <= perRow
                views.setViewVisibility(cell.root, if (collapse) View.GONE else blankMode)
                continue
            }
            views.setViewVisibility(cell.root, View.VISIBLE)
            paintCell(views, cell, profile, active = profile.isActive)
            views.setOnClickPendingIntent(cell.root, applyPendingIntent(context, profile.id))
            views.setContentDescription(
                cell.root,
                if (profile.isActive) "${profile.name}（適用中）" else "${profile.name} を適用",
            )
        }
    }

    private fun renderEmpty(context: Context, views: RemoteViews) {
        val cell = CELLS[0]
        views.setViewVisibility(cell.root, View.VISIBLE)
        views.setInt(cell.bg, "setColorFilter", EMPTY_TINT)
        views.setInt(cell.bg, "setImageAlpha", 200)
        views.setViewVisibility(cell.ring, View.GONE)
        views.setImageViewResource(cell.icon, ProfileIcon.DEFAULT.res)
        views.setInt(cell.icon, "setColorFilter", IDLE_TEXT)
        views.setTextViewText(cell.name, "追加")
        views.setTextColor(cell.name, IDLE_TEXT)
        views.setOnClickPendingIntent(cell.root, openAppPendingIntent(context))
        views.setContentDescription(cell.root, "Volace を開いてプロファイルを作成")
        for (index in 1 until CELLS.size) {
            views.setViewVisibility(CELLS[index].root, View.GONE)
        }
    }

    private fun paintCell(views: RemoteViews, cell: CellRefs, profile: Profile, active: Boolean) {
        views.setInt(cell.bg, "setColorFilter", profile.colorArgb)
        views.setInt(cell.bg, "setImageAlpha", if (active) ACTIVE_ALPHA else IDLE_ALPHA)

        views.setViewVisibility(cell.ring, if (active) View.VISIBLE else View.GONE)
        views.setInt(cell.ring, "setColorFilter", Color.WHITE)
        views.setInt(cell.ring, "setImageAlpha", RING_ALPHA)

        views.setImageViewResource(cell.icon, profile.icon.res)
        // Inactive cells sit on a dim tint of their own colour, so a lightened version of that
        // colour keeps each profile recognisable at a glance without shouting.
        views.setInt(cell.icon, "setColorFilter", if (active) Color.WHITE else lighten(profile.colorArgb, 0.45f))

        views.setTextViewText(cell.name, profile.name)
        views.setTextColor(cell.name, if (active) ACTIVE_TEXT else IDLE_TEXT)
    }

    private fun lighten(color: Int, amount: Float): Int = Color.rgb(
        (Color.red(color) + (255 - Color.red(color)) * amount).toInt(),
        (Color.green(color) + (255 - Color.green(color)) * amount).toInt(),
        (Color.blue(color) + (255 - Color.blue(color)) * amount).toInt(),
    )

    private fun applyPendingIntent(context: Context, profileId: Long): PendingIntent {
        val intent = Intent(context, VolumeApplyReceiver::class.java).apply {
            action = VolumeApplyReceiver.ACTION_APPLY
            // Distinct data makes each PendingIntent a distinct "identity" so FLAG_UPDATE_CURRENT
            // cannot silently hand one profile's extras to another cell.
            data = Uri.parse("volace://profile/$profileId")
            putExtra(VolumeApplyReceiver.EXTRA_PROFILE_ID, profileId)
        }
        return PendingIntent.getBroadcast(
            context,
            profileId.toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun cyclePendingIntent(context: Context): PendingIntent {
        val intent = Intent(context, VolumeApplyReceiver::class.java).apply {
            action = VolumeApplyReceiver.ACTION_CYCLE
            data = Uri.parse("volace://cycle")
        }
        return PendingIntent.getBroadcast(
            context,
            REQUEST_CYCLE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun openAppPendingIntent(context: Context): PendingIntent {
        val intent = Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        return PendingIntent.getActivity(
            context,
            REQUEST_OPEN_APP,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private const val REQUEST_CYCLE = 900_001
    private const val REQUEST_OPEN_APP = 900_002
}
