package com.hong.volace.widget

import com.hong.volace.audio.DndState
import com.hong.volace.audio.DndModes
import com.hong.volace.timer.ProfileTimer
import com.hong.volace.timer.ProfileTimers
import com.hong.volace.timer.timerEndText
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.media.AudioManager
import android.net.Uri
import android.util.SizeF
import android.util.TypedValue
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
import com.hong.volace.data.contentColorOn
import com.hong.volace.data.icon
import com.hong.volace.ui.theme.SkinStore
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
    /** Colours of the chosen skin (light and dark set). */
    val palette: WidgetPalette,
    /** A timed profile running ("15:00 まで"), if any. */
    val timer: ProfileTimer? = null,
    /** "Do Not Disturb" now, for the status read-out. */
    val dnd: DndState = DndState.OFF,
) {
    val active: Profile? get() = profiles.firstOrNull { it.isActive }

    /**
     * "15:00 まで" while a timer runs on the profile in effect; "時間になりました" once it is due
     * but Android refused to go back (the notification and the app offer to do it).
     */
    fun timerCaption(context: Context): String? {
        val timer = timer ?: return null
        if (active?.id != timer.profileId) return null
        if (timer.isDue(System.currentTimeMillis())) return context.getString(R.string.timer_due_title)
        return context.getString(R.string.timer_until, timerEndText(context, timer.endAt))
    }

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
                palette = WidgetPalette.of(context, SkinStore.current(context)),
                timer = ProfileTimers.current(context),
                dnd = DndModes(context).state(),
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


    /** Below this height a 4×2 cannot fit the status strip above two rows of profiles. */
    private const val FULL_STATUS_MIN_HEIGHT_DP = 180f

    /** Below this a 4×1 cannot fit four profiles and the volume tile at a tappable size. */
    private const val ROW_STATUS_MIN_WIDTH_DP = 250f

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
        if (style.status == StatusPanel.MINI) {
            return RemoteViews(
                mapOf(
                    SizeF(110f, 40f) to buildOne(context, style, state, showStatus = false),
                    SizeF(ROW_STATUS_MIN_WIDTH_DP, 40f) to buildOne(context, style, state, showStatus = true),
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
        val p = state.palette
        views.tintBackground(android.R.id.background, p) { it.background }
        if (layout == R.layout.widget_1x1_wide) {
            views.setTextViewText(R.id.pick_title, context.getString(R.string.widget_pick))
            views.setTextViewText(R.id.pick_subtitle, context.getString(R.string.widget_pick_sub))
            views.tintBackground(R.id.pick_button, p) { it.panel }
            views.color(R.id.pick_title, "setTextColor", p) { it.text }
            views.color(R.id.pick_subtitle, "setTextColor", p) { it.subText }
            views.color(R.id.pick_icon, "setColorFilter", p) { it.text }
            views.setOnClickPendingIntent(
                R.id.pick_button,
                if (state.hasAccess) pickerPendingIntent(context) else openAppPendingIntent(context),
            )
            views.setContentDescription(R.id.pick_button, context.getString(R.string.widget_pick_cd))
        }
        if (style.status != StatusPanel.NONE) {
            views.setViewVisibility(R.id.status_panel, if (showStatus) View.VISIBLE else View.GONE)
            if (showStatus) renderStatus(context, views, style.status, state)
        }
        when {
            state.profiles.isEmpty() -> renderEmpty(context, views, style, state.palette)
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
        paintCell(views, cell, current, look, state.palette)
        if (!state.hasAccess) {
            // The only place a 1×1 can say why taps stopped working.
            views.setTextViewText(cell.name, context.getString(R.string.widget_needs_access))
            views.setTextViewText(R.id.cycle_caption, context.getString(R.string.widget_tap_to_open))
            views.setOnClickPendingIntent(cell.root, openAppPendingIntent(context))
            views.setContentDescription(cell.root, context.getString(R.string.widget_cd_needs_access))
            return
        }
        // Spelled out under the name: what the cell shows, and what a tap does.
        views.setTextViewText(
            R.id.cycle_caption,
            when {
                state.active == null -> context.getString(R.string.widget_not_applied)
                look == CellLook.DRIFTED -> context.getString(R.string.changed)
                else -> state.timerCaption(context) ?: context.getString(R.string.widget_tap_next)
            },
        )
        views.color(R.id.cycle_caption, "setTextColor", state.palette) { colors ->
            when (look) {
                CellLook.ACTIVE -> contentColorOn(current.colorArgb) // on the full-colour fill
                CellLook.DRIFTED -> tintFor(current.colorArgb, colors)
                CellLook.IDLE -> colors.subText
            }
        }
        views.setOnClickPendingIntent(cell.root, cyclePendingIntent(context))
        val status = when {
            look == CellLook.DRIFTED -> context.getString(R.string.widget_cd_drifted_suffix)
            else -> state.timerCaption(context)?.let { context.getString(R.string.widget_cd_timer_suffix, it) }.orEmpty()
        }
        views.setContentDescription(cell.root, context.getString(R.string.widget_cd_cycle, current.name, status))
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
            paintCell(views, cell, profile, look, state.palette)
            if (!state.hasAccess) {
                views.setOnClickPendingIntent(cell.root, openAppPendingIntent(context))
                views.setContentDescription(cell.root, context.getString(R.string.widget_cd_needs_access))
                continue
            }
            views.setOnClickPendingIntent(cell.root, applyPendingIntent(context, profile.id))
            views.setContentDescription(
                cell.root,
                when (look) {
                    CellLook.ACTIVE -> context.getString(R.string.widget_cd_active, profile.name)
                    CellLook.DRIFTED -> context.getString(R.string.widget_cd_drifted, profile.name)
                    CellLook.IDLE -> context.getString(R.string.widget_cd_apply, profile.name)
                },
            )
        }
    }

    private fun renderEmpty(context: Context, views: RemoteViews, style: WidgetStyle, palette: WidgetPalette) {
        val cell = CELLS[0]
        views.setViewVisibility(cell.root, View.VISIBLE)
        views.color(cell.bg, "setColorFilter", palette) { it.emptyCell }
        views.setInt(cell.bg, "setImageAlpha", 200)
        views.setViewVisibility(cell.ring, View.GONE)
        views.setImageViewResource(cell.icon, ProfileIcon.DEFAULT.res)
        views.color(cell.icon, "setColorFilter", palette) { it.subText }
        views.setTextViewText(cell.name, context.getString(R.string.widget_add))
        views.color(cell.name, "setTextColor", palette) { it.subText }
        views.setOnClickPendingIntent(cell.root, openAppPendingIntent(context))
        views.setContentDescription(cell.root, context.getString(R.string.widget_cd_create))
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
        val p = state.palette
        // The applied profile's colour, toned for the background; the skin's own accent otherwise.
        val accent = { colors: WidgetPalette.Colors ->
            active?.let { if (colors.isLight) darken(it.colorArgb, 0.25f) else lighten(it.colorArgb, 0.25f) }
                ?: colors.accent
        }
        val mode = state.device.ringerMode

        views.tintBackground(R.id.status_panel, p) { it.panel }
        views.color(R.id.status_mode_icon, "setColorFilter", p) { it.text }

        if (panel == StatusPanel.FULL) {
            // Set from here rather than left to the layout: the launcher resolves layout strings in
            // the system language, which differs from the app's own when one is chosen for Volace.
            views.setTextViewText(R.id.status_title, context.getString(R.string.current_volume))
            views.setTextViewText(R.id.status_open, context.getString(R.string.widget_open_app))
            views.color(R.id.status_title, "setTextColor", p) { it.text }
            views.color(R.id.status_mode_text, "setTextColor", p) { it.text }
        }
        val labelWidth = context.resources.getDimension(R.dimen.widget_level_label_width)
        views.setImageViewResource(
            R.id.status_mode_icon,
            if (state.hasAccess) ringerModeIcon(mode) else R.drawable.ic_widget_warning,
        )
        VolumeStream.entries.forEachIndexed { index, stream ->
            val refs = STATS[index]
            val max = (state.maxes[stream] ?: 1).coerceAtLeast(1)
            val level = state.device.levelOf(stream).coerceIn(0, max)
            views.setTextViewText(
                refs.label,
                if (panel == StatusPanel.FULL) context.getString(stream.label) else stream.shortLabel,
            )
            if (panel == StatusPanel.FULL) {
                views.setViewLayoutWidth(refs.label, labelWidth, TypedValue.COMPLEX_UNIT_PX)
            }
            views.setInt(refs.fill, "setImageLevel", level * MAX_LEVEL / max)
            views.color(refs.fill, "setColorFilter", p, accent)
            views.color(refs.track, "setColorFilter", p) { it.track }
            views.color(refs.label, "setTextColor", p) { it.subText }
            if (panel == StatusPanel.FULL) {
                views.setTextViewText(refs.value, level.toString())
                views.color(refs.value, "setTextColor", p) { it.text }
            }
        }

        val drift = when {
            !state.hasAccess -> context.getString(R.string.widget_needs_access_open)
            state.drifted && active != null -> context.getString(R.string.widget_drift, active.name)
            else -> null
        }
        if (panel == StatusPanel.FULL) {
            views.setTextViewText(
                R.id.status_mode_text,
                listOfNotNull(context.getString(ringerModeLabel(mode)), state.dnd.shortText(context), drift ?: state.timerCaption(context))
                    .joinToString(context.getString(R.string.list_separator)),
            )
            views.color(R.id.status_open, "setTextColor", p, accent)
        } else {
            // No room for sentences in a 4×1 tile: one word for what it is, or what is wrong.
            views.setTextViewText(
                R.id.status_caption,
                when {
                    !state.hasAccess -> context.getString(R.string.widget_caption_needs_access)
                    state.drifted -> context.getString(R.string.changed)
                    else -> state.timerCaption(context)
                        ?: context.getString(R.string.dnd_short).takeIf { state.dnd.active }
                        ?: context.getString(R.string.widget_caption_details)
                },
            )
            views.color(R.id.status_caption, "setTextColor", p) { colors ->
                if (state.drifted || !state.hasAccess) accent(colors) else colors.subText
            }
        }

        views.setOnClickPendingIntent(R.id.status_panel, openAppPendingIntent(context))
        val separator = context.getString(R.string.detail_separator)
        val levels = VolumeStream.entries.joinToString(separator) { stream ->
            "${context.getString(stream.label)} ${state.device.levelOf(stream)}/${state.maxes[stream] ?: 0}"
        }
        views.setContentDescription(
            R.id.status_panel,
            context.getString(
                R.string.widget_cd_status,
                listOf(context.getString(ringerModeLabel(mode)), state.dnd.describe(context))
                    .joinToString(separator),
                levels,
                (drift ?: state.timerCaption(context))?.let { separator + it }.orEmpty(),
            ),
        )
    }

    private fun lookOf(profile: Profile, state: WidgetState): CellLook = when {
        !profile.isActive -> CellLook.IDLE
        state.drifted -> CellLook.DRIFTED
        else -> CellLook.ACTIVE
    }

    private fun paintCell(
        views: RemoteViews,
        cell: CellRefs,
        profile: Profile,
        look: CellLook,
        palette: WidgetPalette,
    ) {
        val active = look == CellLook.ACTIVE
        // On the full-colour fill: white, or near black on light colours (amber, …).
        val onFill = contentColorOn(profile.colorArgb)
        views.setInt(cell.bg, "setColorFilter", profile.colorArgb)
        views.setInt(cell.bg, "setImageAlpha", if (active) ACTIVE_ALPHA else IDLE_ALPHA)

        // A drifted profile keeps an outline in its own colour: "this was applied, but the volume
        // has been changed since" — tapping it again re-applies.
        views.setViewVisibility(cell.ring, if (look == CellLook.IDLE) View.GONE else View.VISIBLE)
        views.color(cell.ring, "setColorFilter", palette) { if (active) onFill else tintFor(profile.colorArgb, it) }
        views.setInt(cell.ring, "setImageAlpha", RING_ALPHA)

        views.setImageViewResource(cell.icon, profile.icon.res)
        // Inactive cells sit on a dim tint of their own colour, so a toned version of that colour
        // keeps each profile recognisable at a glance without shouting.
        views.color(cell.icon, "setColorFilter", palette) { if (active) onFill else tintFor(profile.colorArgb, it) }

        views.setTextViewText(cell.name, profile.name)
        views.color(cell.name, "setTextColor", palette) { if (active) onFill else it.subText }
    }

    /** A profile's colour toned to stand out on the skin's background: lighter on dark, darker on light. */
    private fun tintFor(color: Int, colors: WidgetPalette.Colors): Int =
        if (colors.isLight) darken(color, 0.3f) else lighten(color, 0.45f)

    /** A colour that may differ between the launcher's light and dark mode (AUTO, DYNAMIC). */
    private inline fun RemoteViews.color(
        id: Int,
        method: String,
        palette: WidgetPalette,
        pick: (WidgetPalette.Colors) -> Int,
    ) {
        val day = pick(palette.day)
        val night = pick(palette.night)
        if (day == night) setInt(id, method, day) else setColorInt(id, method, day, night)
    }

    /** Recolours a view's background drawable, keeping its shape (rounded corners). */
    private inline fun RemoteViews.tintBackground(
        id: Int,
        palette: WidgetPalette,
        pick: (WidgetPalette.Colors) -> Int,
    ) = setColorStateList(
        id,
        "setBackgroundTintList",
        ColorStateList.valueOf(pick(palette.day)),
        ColorStateList.valueOf(pick(palette.night)),
    )

    private fun ringerModeIcon(mode: Int): Int = when (mode) {
        AudioManager.RINGER_MODE_SILENT -> R.drawable.ic_profile_volume_off
        AudioManager.RINGER_MODE_VIBRATE -> R.drawable.ic_profile_vibration
        else -> R.drawable.ic_profile_volume_up
    }

    private fun darken(color: Int, amount: Float): Int = Color.rgb(
        (Color.red(color) * (1 - amount)).toInt(),
        (Color.green(color) * (1 - amount)).toInt(),
        (Color.blue(color) * (1 - amount)).toInt(),
    )

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

    /** Full scale of [android.graphics.drawable.ClipDrawable]'s level. */
    private const val MAX_LEVEL = 10_000

    private const val REQUEST_CYCLE = 900_001
    private const val REQUEST_OPEN_APP = 900_002
    private const val REQUEST_PICK = 900_003
}
