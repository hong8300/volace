package com.hong.volace.widget

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.media.AudioManager
import android.net.Uri
import android.util.SizeF
import android.view.View
import android.widget.RemoteViews
import com.hong.volace.MainActivity
import com.hong.volace.R
import com.hong.volace.audio.DeviceVolumes
import com.hong.volace.audio.VolumeApplier
import com.hong.volace.audio.VolumeStream
import com.hong.volace.audio.ringerModeLabel
import com.hong.volace.data.Profile
import com.hong.volace.data.ProfileIcon
import com.hong.volace.data.VolaceDatabase
import com.hong.volace.data.icon
import com.hong.volace.tile.ProfilePickerActivity

/** Everything a widget shows. Read once per refresh and shared by every placed widget. */
internal class WidgetState(
    val profiles: List<Profile>,
    val device: DeviceVolumes,
    val maxes: Map<VolumeStream, Int>,
    /** The active profile no longer matches the device: something else changed the volume. */
    val drifted: Boolean,
    /** "Do Not Disturb" access; without it nothing can be applied, so cells open the app. */
    val hasAccess: Boolean,
) {
    val active: Profile? get() = profiles.firstOrNull { it.isActive }

    companion object {
        suspend fun load(context: Context): WidgetState {
            val profiles = VolaceDatabase.get(context).profileDao().getAllOnce()
            val applier = VolumeApplier(context)
            val device = applier.snapshot()
            val active = profiles.firstOrNull { it.isActive }
            return WidgetState(
                profiles = profiles,
                device = device,
                maxes = VolumeStream.entries.associateWith { applier.maxVolume(it) },
                drifted = active != null && !applier.matches(active, device),
                hasAccess = applier.hasAccess(),
            )
        }
    }
}

/**
 * Builds the [RemoteViews] for a widget from the current profile list and device volumes.
 *
 * Everything a cell shows is driven by remotable setters only ([RemoteViews.setInt] onto
 * `setColorFilter` / `setImageAlpha` / `setImageLevel`), so a single set of shape drawables can be
 * recoloured per profile without needing one drawable per colour.
 */
object WidgetRenderer {

    private const val ACTIVE_ALPHA = 255
    private const val IDLE_ALPHA = 48
    private const val RING_ALPHA = 225

    private val ACTIVE_TEXT = Color.WHITE
    private val IDLE_TEXT = 0xFFDCDCE6.toInt()
    private val EMPTY_TINT = 0xFF3B3B4A.toInt()

    /** Bar colour while no profile has been applied yet (the app's own primary). */
    private val NEUTRAL_BAR = 0xFF8FB8FF.toInt()

    /** Below this height a 4×2 cannot fit the status strip above two rows of profiles. */
    private const val FULL_STATUS_MIN_HEIGHT_DP = 180f

    /** About two home-screen cells: from here the 1×1 has room for the "選ぶ" tile. */
    private const val WIDE_SINGLE_MIN_WIDTH_DP = 150f

    /** Applied, and the device still matches it; applied but changed since; not applied. */
    private enum class CellLook { ACTIVE, DRIFTED, IDLE }

    internal fun build(context: Context, style: WidgetStyle, state: WidgetState): RemoteViews {
        if (style.cycles) {
            // Widened to two cells or more, the 1×1 also gets a "選ぶ" tile (WIDE_SINGLE_MIN_WIDTH_DP).
            return RemoteViews(
                mapOf(
                    SizeF(40f, 40f) to buildOne(context, style, state, showStatus = true),
                    SizeF(WIDE_SINGLE_MIN_WIDTH_DP, 40f) to
                        buildOne(context, style, state, showStatus = true, layout = R.layout.widget_1x1_wide),
                ),
            )
        }
        if (style.status != StatusPanel.FULL) return buildOne(context, style, state, showStatus = true)
        // Let the launcher pick per placed widget, so shrinking a 4×2 drops the strip rather than
        // squashing the profile buttons.
        return RemoteViews(
            mapOf(
                SizeF(110f, 110f) to buildOne(context, style, state, showStatus = false),
                SizeF(110f, FULL_STATUS_MIN_HEIGHT_DP) to buildOne(context, style, state, showStatus = true),
            ),
        )
    }

    private fun buildOne(
        context: Context,
        style: WidgetStyle,
        state: WidgetState,
        showStatus: Boolean,
        layout: Int = style.layout,
    ): RemoteViews {
        val views = RemoteViews(context.packageName, layout)
        if (layout == R.layout.widget_1x1_wide) {
            views.setOnClickPendingIntent(
                R.id.pick_button,
                if (state.hasAccess) pickerPendingIntent(context) else openAppPendingIntent(context),
            )
            views.setContentDescription(R.id.pick_button, "一覧からプロファイルを選ぶ")
        }
        if (style.status != StatusPanel.NONE) {
            views.setViewVisibility(R.id.status_panel, if (showStatus) View.VISIBLE else View.GONE)
            if (showStatus) renderStatus(context, views, style.status, state)
        }
        when {
            state.profiles.isEmpty() -> renderEmpty(context, views, style)
            style.cycles -> renderCycle(context, views, state)
            else -> renderGrid(context, views, style, state)
        }
        return views
    }

    /** One-cell widget: shows the profile in effect, tapping steps to the next one. */
    private fun renderCycle(context: Context, views: RemoteViews, state: WidgetState) {
        val current = state.active ?: state.profiles.first()
        val cell = CELLS[0]
        val look = lookOf(current, state)
        paintCell(views, cell, current, look)
        if (!state.hasAccess) {
            // The only place a 1×1 can say why taps stopped working.
            views.setTextViewText(cell.name, "許可が必要")
            views.setTextViewText(R.id.cycle_caption, "タップして開く")
            views.setOnClickPendingIntent(cell.root, openAppPendingIntent(context))
            views.setContentDescription(cell.root, NEEDS_ACCESS)
            return
        }
        // Spelled out under the name: what the cell shows, and what a tap does.
        views.setTextViewText(
            R.id.cycle_caption,
            when {
                state.active == null -> "未適用・タップで適用"
                look == CellLook.DRIFTED -> "変更あり"
                else -> "タップで次へ"
            },
        )
        views.setTextColor(
            R.id.cycle_caption,
            when (look) {
                CellLook.ACTIVE -> 0xCCFFFFFF.toInt() // on the full-colour fill
                CellLook.DRIFTED -> lighten(current.colorArgb, 0.45f)
                CellLook.IDLE -> IDLE_TEXT
            },
        )
        views.setOnClickPendingIntent(cell.root, cyclePendingIntent(context))
        val status = if (look == CellLook.DRIFTED) "（適用後に音量が変更されています）" else ""
        views.setContentDescription(cell.root, "現在: ${current.name}$status。タップで次のプロファイル")
    }

    private fun renderGrid(
        context: Context,
        views: RemoteViews,
        style: WidgetStyle,
        state: WidgetState,
    ) {
        val shown = state.profiles.take(style.cellCount)

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
            val look = lookOf(profile, state)
            views.setViewVisibility(cell.root, View.VISIBLE)
            paintCell(views, cell, profile, look)
            if (!state.hasAccess) {
                views.setOnClickPendingIntent(cell.root, openAppPendingIntent(context))
                views.setContentDescription(cell.root, NEEDS_ACCESS)
                continue
            }
            views.setOnClickPendingIntent(cell.root, applyPendingIntent(context, profile.id))
            views.setContentDescription(
                cell.root,
                when (look) {
                    CellLook.ACTIVE -> "${profile.name}（適用中）"
                    CellLook.DRIFTED -> "${profile.name}（適用後に音量が変更されています）。タップで再適用"
                    CellLook.IDLE -> "${profile.name} を適用"
                },
            )
        }
    }

    private fun renderEmpty(context: Context, views: RemoteViews, style: WidgetStyle) {
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
        // Otherwise the empty row keeps its weight and leaves the lower half blank.
        style.secondRow?.let { views.setViewVisibility(it, View.GONE) }
    }

    /**
     * The device's actual volumes, whoever set them. Bars are scaled per stream, like the in-app
     * list, and take the applied profile's colour so the strip reads as "this profile's state".
     */
    private fun renderStatus(
        context: Context,
        views: RemoteViews,
        panel: StatusPanel,
        state: WidgetState,
    ) {
        val active = state.active
        val barColor = active?.let { lighten(it.colorArgb, 0.25f) } ?: NEUTRAL_BAR
        val mode = state.device.ringerMode

        views.setImageViewResource(
            R.id.status_mode_icon,
            if (state.hasAccess) ringerModeIcon(mode) else R.drawable.ic_widget_warning,
        )
        VolumeStream.entries.forEachIndexed { index, stream ->
            val refs = STATS[index]
            val max = (state.maxes[stream] ?: 1).coerceAtLeast(1)
            val level = state.device.levelOf(stream).coerceIn(0, max)
            views.setTextViewText(refs.label, if (panel == StatusPanel.FULL) stream.label else stream.shortLabel)
            views.setInt(refs.fill, "setImageLevel", level * MAX_LEVEL / max)
            views.setInt(refs.fill, "setColorFilter", barColor)
            if (panel == StatusPanel.FULL) views.setTextViewText(refs.value, level.toString())
        }

        val drift = when {
            !state.hasAccess -> "許可が必要です（タップして開く）"
            state.drifted && active != null -> "「${active.name}」から変更あり"
            else -> null
        }
        if (panel == StatusPanel.FULL) {
            views.setTextViewText(
                R.id.status_mode_text,
                listOfNotNull(ringerModeLabel(mode), drift).joinToString("・"),
            )
            views.setTextColor(R.id.status_open, barColor)
        } else {
            // No room for sentences in a 4×1 tile: one word for what it is, or what is wrong.
            views.setTextViewText(
                R.id.status_caption,
                when {
                    !state.hasAccess -> "要許可"
                    state.drifted -> "変更あり"
                    else -> "音量詳細"
                },
            )
            views.setTextColor(
                R.id.status_caption,
                if (state.drifted || !state.hasAccess) barColor else IDLE_TEXT,
            )
        }

        views.setOnClickPendingIntent(R.id.status_panel, openAppPendingIntent(context))
        val levels = VolumeStream.entries.joinToString("、") { stream ->
            "${stream.label} ${state.device.levelOf(stream)}/${state.maxes[stream] ?: 0}"
        }
        views.setContentDescription(
            R.id.status_panel,
            "現在の音量: ${ringerModeLabel(mode)}、$levels${drift?.let { "、$it" } ?: ""}。タップで Volace を開く",
        )
    }

    private fun lookOf(profile: Profile, state: WidgetState): CellLook = when {
        !profile.isActive -> CellLook.IDLE
        state.drifted -> CellLook.DRIFTED
        else -> CellLook.ACTIVE
    }

    private fun paintCell(views: RemoteViews, cell: CellRefs, profile: Profile, look: CellLook) {
        val active = look == CellLook.ACTIVE
        views.setInt(cell.bg, "setColorFilter", profile.colorArgb)
        views.setInt(cell.bg, "setImageAlpha", if (active) ACTIVE_ALPHA else IDLE_ALPHA)

        // A drifted profile keeps an outline in its own colour: "this was applied, but the volume
        // has been changed since" — tapping it again re-applies.
        views.setViewVisibility(cell.ring, if (look == CellLook.IDLE) View.GONE else View.VISIBLE)
        views.setInt(cell.ring, "setColorFilter", if (active) Color.WHITE else lighten(profile.colorArgb, 0.45f))
        views.setInt(cell.ring, "setImageAlpha", RING_ALPHA)

        views.setImageViewResource(cell.icon, profile.icon.res)
        // Inactive cells sit on a dim tint of their own colour, so a lightened version of that
        // colour keeps each profile recognisable at a glance without shouting.
        views.setInt(cell.icon, "setColorFilter", if (active) Color.WHITE else lighten(profile.colorArgb, 0.45f))

        views.setTextViewText(cell.name, profile.name)
        views.setTextColor(cell.name, if (active) ACTIVE_TEXT else IDLE_TEXT)
    }

    private fun ringerModeIcon(mode: Int): Int = when (mode) {
        AudioManager.RINGER_MODE_SILENT -> R.drawable.ic_profile_volume_off
        AudioManager.RINGER_MODE_VIBRATE -> R.drawable.ic_profile_vibration
        else -> R.drawable.ic_profile_volume_up
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

    /** The chooser the Quick Settings tile uses (applies from a visible activity). */
    private fun pickerPendingIntent(context: Context): PendingIntent =
        PendingIntent.getActivity(
            context,
            REQUEST_PICK,
            Intent(context, ProfilePickerActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    /** Opens the app exactly like its launcher icon does. */
    private fun openAppPendingIntent(context: Context): PendingIntent {
        val intent = MainActivity.launcherIntent(context)
        return PendingIntent.getActivity(
            context,
            REQUEST_OPEN_APP,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private const val NEEDS_ACCESS =
        "「サイレント モードへのアクセス」が必要です。タップして Volace を開き、許可してください"

    /** Full scale of [android.graphics.drawable.ClipDrawable]'s level. */
    private const val MAX_LEVEL = 10_000

    private const val REQUEST_CYCLE = 900_001
    private const val REQUEST_OPEN_APP = 900_002
    private const val REQUEST_PICK = 900_003
}
