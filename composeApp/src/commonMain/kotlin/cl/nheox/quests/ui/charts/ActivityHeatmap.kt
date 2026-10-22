package cl.nheox.quests.ui.charts

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import cl.nheox.quests.domain.DayActivity
import cl.nheox.quests.domain.Dates
import cl.nheox.quests.domain.Standing
import cl.nheox.quests.ui.theme.Motion
import cl.nheox.quests.ui.theme.Radii
import cl.nheox.quests.ui.theme.Space
import cl.nheox.quests.ui.theme.strings
import cl.nheox.quests.ui.theme.theme
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.Month
import java.time.temporal.ChronoUnit
import kotlin.math.floor

/** Where one day sits in the grid: columns are ISO weeks, rows Monday to Sunday. */
internal data class HeatmapCell(
    val day: DayActivity,
    val column: Int,
    val row: Int,
)

/** A month name over the column holding that month's first day. */
internal data class MonthLabel(
    val column: Int,
    val month: Month,
)

internal data class HeatmapLayout(
    val columns: Int,
    val cells: List<HeatmapCell>,
    val months: List<MonthLabel>,
)

/** Columns a month label needs to itself, so "Sep" and "Oct" never touch. */
internal const val MONTH_LABEL_COLUMNS: Int = 3

/**
 * Lays consecutive days out as a calendar grid.
 *
 * A column is an ISO week, Monday at the top, so 84 days ending on a Thursday
 * fill thirteen columns: the first holds the oldest Friday to Sunday, the last
 * Monday to today, and between them they make up the one week the eleven full
 * columns leave over. Nothing is drawn for the days after today.
 *
 * Each month is named over the column its first day falls in, plus the month
 * the grid opens in — unless the next name would crowd it.
 */
internal fun heatmapLayout(days: List<DayActivity>): HeatmapLayout {
    if (days.isEmpty()) return HeatmapLayout(columns = 0, cells = emptyList(), months = emptyList())
    val firstWeek = Dates.startOfWeek(days.first().date)
    val cells = days.map { day ->
        HeatmapCell(
            day = day,
            column = ChronoUnit.WEEKS.between(firstWeek, Dates.startOfWeek(day.date)).toInt(),
            row = day.date.dayOfWeek.value - 1,
        )
    }
    val starts = cells.filter { it.day.date.dayOfMonth == 1 }.map { MonthLabel(it.column, it.day.date.month) }
    val opening = MonthLabel(0, days.first().date.month)
    val months = when {
        starts.firstOrNull()?.let { it.column < MONTH_LABEL_COLUMNS } == true -> starts
        else -> listOf(opening) + starts
    }
    return HeatmapLayout(columns = cells.last().column + 1, cells = cells, months = months)
}

private val DayGutter = 32.dp
private val MonthRow = 18.dp
private val TooltipGap = 6.dp

/** Cell plus gap, at its smallest and largest: legible at one end, not bloated at the other. */
private val MinPitch = 12.dp
private val MaxPitch = 26.dp

/** The share of a pitch the gap takes, so cells and gaps scale together. */
private const val GAP_SHARE = 0.2f

/**
 * The cell pitch that fits [columns] weeks beside the day labels in
 * [available], within [MinPitch]..[MaxPitch].
 */
internal fun heatmapPitch(available: Dp, columns: Int): Dp =
    ((available - DayGutter) / columns.coerceAtLeast(1)).coerceIn(MinPitch, MaxPitch)

/** Where on the 0..1 ramp from the card to the accent each non-empty shade sits. */
private val HeatStops = listOf(0.35f, 0.58f, 0.8f, 1f)

/**
 * The [Standing.HEAT_LEVELS] shades, from an empty day to full teal.
 *
 * Teal, as in the owner's dashboard layout: gold is kept for XP figures, and
 * the two would otherwise compete on the same screen. An empty day is drawn in
 * the border tone rather than left out, so the grid still reads as a calendar
 * before anything is in it.
 */
@Composable
internal fun heatShades(): List<Color> {
    val card = theme.card
    val full = theme.accent
    return listOf(theme.border) + HeatStops.map { lerp(card, full, it) }
}

/**
 * The activity heatmap (§7.4): the last twelve weeks, one cell per day,
 * shaded by the XP earned that day, with today outlined.
 *
 * Its cells are sized from [availableWidth] — the width the caller has for it
 * — rather than measured from the constraints, so it can sit in a row of
 * cards that share one height, which measures its children twice.
 *
 * Hovering a cell names the day and what it held. The grid is a picture, so a
 * screen reader hears [description] — the totals it summarises — instead of
 * eighty-four cells.
 */
@Composable
public fun ActivityHeatmap(
    days: List<DayActivity>,
    today: LocalDate,
    availableWidth: Dp,
    description: String,
    modifier: Modifier = Modifier,
) {
    val text = strings
    val layout = remember(days) { heatmapLayout(days) }
    val pitch = heatmapPitch(availableWidth, layout.columns)
    val gap = pitch * GAP_SHARE
    val gridWidth = pitch * layout.columns - gap
    val gridHeight = pitch * 7 - gap
    val labelStyle = MaterialTheme.typography.labelMedium

    Column(modifier.clearAndSetSemantics { contentDescription = description }) {
        Box(Modifier.padding(start = DayGutter).width(gridWidth).height(MonthRow)) {
            layout.months.forEach { label ->
                Text(
                    text.monthShort(label.month),
                    style = labelStyle,
                    color = theme.textMuted,
                    maxLines = 1,
                    modifier = Modifier.offset(x = pitch * label.column),
                )
            }
        }
        Row {
            Box(Modifier.width(DayGutter).height(gridHeight)) {
                // Every other day, as calendars do: seven labels on rows this
                // small would sit on top of each other.
                listOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY, DayOfWeek.SUNDAY).forEach { day ->
                    Box(
                        Modifier.offset(y = pitch * (day.value - 1)).height(pitch - gap),
                        contentAlignment = Alignment.CenterStart,
                    ) {
                        Text(text.weekdayShort(day), style = labelStyle, color = theme.textMuted, maxLines = 1)
                    }
                }
            }
            HeatmapGrid(layout, today, pitch, Modifier.size(gridWidth, gridHeight))
        }
    }
}

/**
 * The cells, and the tooltip over whichever one the pointer is on.
 *
 * The tooltip is drawn into the grid's own canvas rather than laid over it as
 * a second element, so it can never catch the pointer it is following — a
 * tooltip that does flickers whenever the pointer crosses onto it. It stays
 * inside the grid's bounds, below a cell in the top three rows and above it in
 * the rest, so neither the legend nor the next card can cover it.
 */
@Composable
private fun HeatmapGrid(layout: HeatmapLayout, today: LocalDate, pitchDp: Dp, modifier: Modifier) {
    val text = strings
    val shades = heatShades()
    val scale = remember(layout) { Standing.heatScale(layout.cells.map { it.day }) }
    val byPosition = remember(layout) { layout.cells.associateBy { it.column to it.row } }
    val todayCell = remember(layout, today) { layout.cells.firstOrNull { it.day.date == today } }
    val todayOutline = theme.text
    val hoverOutline = theme.textMuted
    val tooltipFill = theme.text
    val tooltipText = theme.background
    val tooltipStyle = MaterialTheme.typography.labelMedium.copy(fontFeatureSettings = "tnum")
    val measurer = rememberTextMeasurer()

    var hovered by remember { mutableStateOf<HeatmapCell?>(null) }
    // The last cell hovered, kept so the tooltip still has something to say
    // while it fades out after the pointer leaves.
    var shown by remember { mutableStateOf<HeatmapCell?>(null) }
    val tooltipAlpha by animateFloatAsState(if (hovered != null) 1f else 0f, Motion.tween(Motion.SHORT))
    val tooltip = remember(shown, text, tooltipStyle) {
        shown?.let { cell -> measurer.measure(text.heatmapDay(cell.day.date, cell.day.xp, cell.day.completed), tooltipStyle) }
    }

    Canvas(
        modifier.pointerInput(layout, pitchDp) {
            val pitch = pitchDp.toPx()
            awaitPointerEventScope {
                while (true) {
                    val event = awaitPointerEvent()
                    val position = event.changes.firstOrNull()?.position
                    // A gap belongs to the cell before it, so the tooltip
                    // does not blink off between two cells.
                    val cell = if (event.type == PointerEventType.Exit || position == null) {
                        null
                    } else {
                        byPosition[floor(position.x / pitch).toInt() to floor(position.y / pitch).toInt()]
                    }
                    hovered = cell
                    if (cell != null) shown = cell
                }
            }
        },
    ) {
        val pitch = pitchDp.toPx()
        val cellPx = pitch * (1f - GAP_SHARE)
        val radius = CornerRadius(3.dp.toPx())

        layout.cells.forEach { cell ->
            drawRoundRect(
                color = shades[Standing.heatLevel(cell.day, scale)],
                topLeft = Offset(cell.column * pitch, cell.row * pitch),
                size = Size(cellPx, cellPx),
                cornerRadius = radius,
            )
        }

        fun outline(cell: HeatmapCell, colour: Color, width: Float) {
            val inset = width / 2f
            drawRoundRect(
                color = colour,
                topLeft = Offset(cell.column * pitch + inset, cell.row * pitch + inset),
                size = Size(cellPx - width, cellPx - width),
                cornerRadius = radius,
                style = Stroke(width),
            )
        }
        hovered?.let { outline(it, hoverOutline, 1.dp.toPx()) }
        todayCell?.let { outline(it, todayOutline, 1.5.dp.toPx()) }

        val tip = shown
        if (tip != null && tooltip != null && tooltipAlpha > 0f) {
            val padX = Space.sm.toPx()
            val padY = Space.xs.toPx()
            val width = tooltip.size.width + padX * 2
            val height = tooltip.size.height + padY * 2
            val gap = TooltipGap.toPx()
            val x = (tip.column * pitch + cellPx / 2f - width / 2f).coerceIn(0f, (size.width - width).coerceAtLeast(0f))
            val y = if (tip.row < 3) tip.row * pitch + cellPx + gap else tip.row * pitch - gap - height
            drawRoundRect(
                color = tooltipFill,
                topLeft = Offset(x, y),
                size = Size(width, height),
                cornerRadius = CornerRadius(Radii.sm.toPx()),
                alpha = tooltipAlpha,
            )
            drawText(tooltip, color = tooltipText, topLeft = Offset(x + padX, y + padY), alpha = tooltipAlpha)
        }
    }
}
