package com.goalmaker.app.ui.calendar

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.layoutId
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.goalmaker.app.R
import com.goalmaker.app.application.planning.AreaItem
import com.goalmaker.app.application.planning.EventBar
import com.goalmaker.app.ui.theme.AppTheme

/**
 * A week row's event bars (docs/calendar.md, Events), drawn over the row's cells: each bar spans its
 * columns in its lane, in its area's colour or the accent, with the title on it. At most three lanes
 * show; a day with more events says "+N" under them. A bar reads as "Prague, 12 to 15 October"; it
 * takes no taps, so a tap on it opens the day under it.
 */
@Composable
internal fun EventLanes(bars: List<EventBar>, areaOf: (String?) -> AreaItem?, modifier: Modifier = Modifier) {
    if (bars.isEmpty()) return
    val lane = laneHeight()
    val hidden = hiddenByColumn(bars)
    Column(verticalArrangement = Arrangement.spacedBy(LANE_GAP), modifier = modifier) {
        for (shown in 0 until minOf(bars.maxOf { it.lane } + 1, MAX_LANES)) {
            Columns(Modifier.fillMaxWidth().height(lane)) {
                bars.filter { it.lane == shown }.forEach { bar -> BarPiece(bar, areaOf(bar.event.areaId)) }
            }
        }
        if (hidden.any { it > 0 }) {
            Columns(Modifier.fillMaxWidth().height(lane)) {
                hidden.forEachIndexed { column, count ->
                    if (count > 0) {
                        Text(
                            stringResource(R.string.calendar_more_events, count),
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = BAR_TEXT),
                            color = AppTheme.colors.textMuted,
                            textAlign = TextAlign.Center,
                            maxLines = 1,
                            // The cell's description already counts the day's events.
                            modifier = Modifier.columns(column, column).clearAndSetSemantics {},
                        )
                    }
                }
            }
        }
    }
}

/** The room a week row's bars take at the bottom of its cells, so the cells grow to hold them. */
@Composable
internal fun eventSpace(bars: List<EventBar>): Dp {
    if (bars.isEmpty()) return 0.dp
    val rows = minOf(bars.maxOf { it.lane } + 1, MAX_LANES) + if (hiddenByColumn(bars).any { it > 0 }) 1 else 0
    return (laneHeight() + LANE_GAP) * rows
}

/** One piece of an event's bar: square where the event goes on past the row, round where it ends. */
@Composable
private fun BarPiece(bar: EventBar, area: AreaItem?) {
    val palette = area?.let { chosen -> AppTheme.areaColors.firstOrNull { it.id == chosen.colorId } }
    val fill = palette?.let { AppTheme.colors.areaContainer(it) } ?: AppTheme.colors.accent
    val ink = palette?.let { AppTheme.colors.areaContent(it) } ?: AppTheme.colors.onAccent
    val description = stringResource(R.string.calendar_event_span, bar.event.title, eventDays(bar.event.startsOn, bar.event.endsOn))
    val round = 4.dp
    Box(
        contentAlignment = Alignment.CenterStart,
        modifier = Modifier
            .columns(bar.from, bar.to)
            .padding(start = if (bar.before) 0.dp else END_INSET, end = if (bar.after) 0.dp else END_INSET)
            .fillMaxSize()
            .clip(
                RoundedCornerShape(
                    topStart = if (bar.before) 0.dp else round,
                    bottomStart = if (bar.before) 0.dp else round,
                    topEnd = if (bar.after) 0.dp else round,
                    bottomEnd = if (bar.after) 0.dp else round,
                ),
            )
            .background(fill)
            .semantics { contentDescription = description },
    ) {
        Text(
            bar.event.title,
            style = MaterialTheme.typography.labelSmall.copy(fontSize = BAR_TEXT, lineHeight = BAR_TEXT),
            color = ink,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Clip,
            modifier = Modifier.padding(horizontal = 3.dp).clearAndSetSemantics {},
        )
    }
}

/** Lays its children out over a week's seven columns, each child where its [columns] say. */
@Composable
private fun Columns(modifier: Modifier, content: @Composable () -> Unit) {
    Layout(content = content, modifier = modifier) { measurables, constraints ->
        val width = constraints.maxWidth / 7f
        val height = constraints.maxHeight
        val placeables = measurables.map { measurable ->
            @Suppress("UNCHECKED_CAST")
            val (from, to) = measurable.layoutId as? Pair<Int, Int> ?: (0 to 6)
            val left = (from * width).toInt()
            val right = ((to + 1) * width).toInt()
            left to measurable.measure(Constraints.fixed(right - left, height))
        }
        layout(constraints.maxWidth, height) {
            placeables.forEach { (left, placeable) -> placeable.place(left, 0) }
        }
    }
}

/** Puts a child of [Columns] over the columns [from] to [to]. */
private fun Modifier.columns(from: Int, to: Int): Modifier = this.layoutId(from to to)

// How many of each column's events sit past the lanes a cell shows.
private fun hiddenByColumn(bars: List<EventBar>): List<Int> =
    (0..6).map { column -> bars.count { it.lane >= MAX_LANES && column in it.from..it.to } }

// A lane holds one line of the bar's small text, so it grows with the system's text size.
@Composable
private fun laneHeight(): Dp = with(LocalDensity.current) { BAR_TEXT.toDp() } + 4.dp

private const val MAX_LANES = 3
private val LANE_GAP = 2.dp
private val END_INSET = 4.dp
private val BAR_TEXT = 10.sp
