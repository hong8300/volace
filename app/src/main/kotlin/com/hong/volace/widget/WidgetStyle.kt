package com.hong.volace.widget

import android.appwidget.AppWidgetProvider
import androidx.annotation.LayoutRes
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
    val title: String,
    val subtitle: String,
) {
    SINGLE(
        R.layout.widget_1x1, 1, Volace1x1Provider::class.java,
        cycles = true, secondRow = null,
        title = "1 × 1", subtitle = "タップするたびに次のプロファイルへ切り替え",
    ),
    ROW4(
        R.layout.widget_1x4, 4, Volace1x4Provider::class.java,
        cycles = false, secondRow = null,
        title = "4 × 1", subtitle = "先頭4件を横一列に表示",
    ),
    GRID8(
        R.layout.widget_2x4, 8, Volace2x4Provider::class.java,
        cycles = false, secondRow = R.id.row_1,
        title = "4 × 2", subtitle = "先頭8件を2段グリッドで表示",
    ),
}
