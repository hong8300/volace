package com.hong.volace.widget

import android.appwidget.AppWidgetProvider
import androidx.annotation.LayoutRes
import androidx.annotation.StringRes
import com.hong.volace.R

/** The four view ids that make up one tappable profile cell in a widget layout. */
internal data class CellRefs(
    val root: Int,
    val bg: Int,
    val ring: Int,
    val icon: Int,
    val name: Int,
)

internal val CELLS: List<CellRefs> = listOf(
    CellRefs(R.id.cell_0, R.id.cell_0_bg, R.id.cell_0_ring, R.id.cell_0_icon, R.id.cell_0_name),
    CellRefs(R.id.cell_1, R.id.cell_1_bg, R.id.cell_1_ring, R.id.cell_1_icon, R.id.cell_1_name),
    CellRefs(R.id.cell_2, R.id.cell_2_bg, R.id.cell_2_ring, R.id.cell_2_icon, R.id.cell_2_name),
    CellRefs(R.id.cell_3, R.id.cell_3_bg, R.id.cell_3_ring, R.id.cell_3_icon, R.id.cell_3_name),
    CellRefs(R.id.cell_4, R.id.cell_4_bg, R.id.cell_4_ring, R.id.cell_4_icon, R.id.cell_4_name),
    CellRefs(R.id.cell_5, R.id.cell_5_bg, R.id.cell_5_ring, R.id.cell_5_icon, R.id.cell_5_name),
    CellRefs(R.id.cell_6, R.id.cell_6_bg, R.id.cell_6_ring, R.id.cell_6_icon, R.id.cell_6_name),
    CellRefs(R.id.cell_7, R.id.cell_7_bg, R.id.cell_7_ring, R.id.cell_7_icon, R.id.cell_7_name),
)

/** View ids of one stream's bar in the status panel. [value] only exists in the full panel. */
internal data class StatRefs(
    val label: Int,
    val track: Int,
    val fill: Int,
    val value: Int,
)

/** One entry per [com.hong.volace.audio.VolumeStream], in the same order. */
internal val STATS: List<StatRefs> = listOf(
    StatRefs(R.id.stat_0_label, R.id.stat_0_track, R.id.stat_0_fill, R.id.stat_0_value),
    StatRefs(R.id.stat_1_label, R.id.stat_1_track, R.id.stat_1_fill, R.id.stat_1_value),
    StatRefs(R.id.stat_2_label, R.id.stat_2_track, R.id.stat_2_fill, R.id.stat_2_value),
    StatRefs(R.id.stat_3_label, R.id.stat_3_track, R.id.stat_3_fill, R.id.stat_3_value),
    StatRefs(R.id.stat_4_label, R.id.stat_4_track, R.id.stat_4_fill, R.id.stat_4_value),
    StatRefs(R.id.stat_5_label, R.id.stat_5_track, R.id.stat_5_fill, R.id.stat_5_value),
)

/** How a widget shows the device's current volumes (its tap target opens the app). */
enum class StatusPanel {
    /** No room: the one-cell widget. */
    NONE,

    /** A fifth tile of six tiny vertical bars labelled R N M A V S, like the in-app list. */
    MINI,

    /** A strip of labelled horizontal bars with values, like the edit screen. */
    FULL,
}

/**
 * The widget variants offered in the launcher's picker. Each one is a separate
 * [AppWidgetProvider] so the user can place several different shapes side by side.
 */
enum class WidgetStyle(
    @param:LayoutRes val layout: Int,
    val cellCount: Int,
    val provider: Class<out AppWidgetProvider>,
    /** A one-cell widget cannot list profiles, so tapping it steps to the next one instead. */
    val cycles: Boolean,
    /** Second row id, hidden when there is nothing to put in it. */
    val secondRow: Int?,
    val status: StatusPanel,
    val title: String,
    @StringRes val subtitle: Int,
) {
    SINGLE(
        R.layout.widget_1x1, 1, Volace1x1Provider::class.java,
        cycles = true, secondRow = null, status = StatusPanel.NONE,
        title = "1 × 1", subtitle = R.string.widget_style_1x1,
    ),
    ROW4(
        R.layout.widget_1x4, 4, Volace1x4Provider::class.java,
        cycles = false, secondRow = null, status = StatusPanel.MINI,
        title = "4 × 1", subtitle = R.string.widget_style_1x4,
    ),
    GRID8(
        R.layout.widget_2x4, 8, Volace2x4Provider::class.java,
        cycles = false, secondRow = R.id.row_1, status = StatusPanel.FULL,
        title = "4 × 2", subtitle = R.string.widget_style_2x4,
    ),
}
