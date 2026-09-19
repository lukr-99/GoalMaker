package com.goalmaker.app.ui.habits

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.goalmaker.app.R
import com.goalmaker.app.application.planning.HabitHeat
import com.goalmaker.app.ui.theme.AppTheme
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlin.math.max
import kotlin.math.min

/**
 * A habit's heatmap (spec, story 42): a column per week, Monday at the top, each day in the accent as
 * strong as its share of the target. A paused day is an outline, a skipped one a dash, a day that isn't
 * due stays empty. Shows the last weeks that fit, with the months above.
 */
@Composable
fun HabitHeatmap(row: HabitRow, modifier: Modifier = Modifier) {
    val heat = row.heat
    if (heat.isEmpty()) return
    val colors = AppTheme.colors
    val locale = LocalConfiguration.current.locales[0]
    val dueDays = heat.count { it is HabitHeat.Share }
    val fullDays = heat.count { it is HabitHeat.Share && it.fraction >= 1.0 }
    val summary = pluralStringResource(R.plurals.habits_heat_summary, dueDays, fullDays, dueDays)
    BoxWithConstraints(modifier.fillMaxWidth().semantics { contentDescription = summary }) {
        val allWeeks = (heat.size + 6) / 7
        val weeks = max(1, min(allWeeks, ((maxWidth + GAP) / (CELL + GAP)).toInt()))
        val skipDays = (allWeeks - weeks) * 7
        val firstDay = row.heatStart.plusDays(skipDays.toLong())
        val shown = heat.drop(skipDays)
        val width = CELL * weeks + GAP * (weeks - 1)
        Column {
            MonthLabels(firstDay, weeks, locale)
            Canvas(Modifier.width(width).height(CELL * 7 + GAP * 6)) {
                val cell = CELL.toPx()
                val gap = GAP.toPx()
                val corner = CornerRadius(cell / 4)
                shown.forEachIndexed { index, day ->
                    val topLeft = Offset((index / 7) * (cell + gap), (index % 7) * (cell + gap))
                    val size = Size(cell, cell)
                    when (day) {
                        // A day before the start or off duty: a hint of the grid, nothing more.
                        HabitHeat.None -> drawRoundRect(colors.outline.copy(alpha = 0.07f), topLeft, size, corner)
                        HabitHeat.Paused -> drawRoundRect(colors.textMuted.copy(alpha = 0.6f), topLeft, size, corner, style = Stroke(cell / 6))
                        HabitHeat.Skipped -> {
                            drawRoundRect(colors.outline.copy(alpha = 0.18f), topLeft, size, corner)
                            drawLine(
                                colors.textMuted,
                                Offset(topLeft.x + cell * 0.25f, topLeft.y + cell / 2),
                                Offset(topLeft.x + cell * 0.75f, topLeft.y + cell / 2),
                                strokeWidth = cell / 6,
                            )
                        }
                        is HabitHeat.Share -> {
                            val color = if (day.fraction <= 0.0) {
                                colors.outline.copy(alpha = 0.18f)
                            } else {
                                colors.accent.copy(alpha = 0.3f + 0.7f * day.fraction.toFloat())
                            }
                            drawRoundRect(color, topLeft, size, corner)
                        }
                    }
                }
                // Today, outlined in the accent.
                val last = shown.lastIndex
                if (last >= 0) {
                    drawRoundRect(
                        colors.accent,
                        Offset((last / 7) * (cell + gap) - gap / 2, (last % 7) * (cell + gap) - gap / 2),
                        Size(cell + gap, cell + gap),
                        CornerRadius(cell / 3),
                        style = Stroke(gap * 0.75f),
                    )
                }
            }
        }
    }
}

// The months' short names over the weeks they start in, when there's room for them.
@Composable
private fun MonthLabels(firstDay: LocalDate, weeks: Int, locale: java.util.Locale) {
    val format = DateTimeFormatter.ofPattern("LLL", locale)
    val labels = (0 until weeks).mapNotNull { week ->
        val monday = firstDay.plusWeeks(week.toLong())
        val sunday = monday.plusDays(6)
        when {
            week == 0 && monday.dayOfMonth <= 7 -> week to format.format(monday)
            week > 0 && sunday.dayOfMonth <= 7 -> week to format.format(sunday)
            else -> null
        }
    }
    val kept = labels.fold(emptyList<Pair<Int, String>>()) { acc, label ->
        if (acc.isEmpty() || label.first - acc.last().first >= 3) acc + label else acc
    }
    Layout(
        content = {
            kept.forEach { (_, text) ->
                Text(text, style = MaterialTheme.typography.labelSmall, color = AppTheme.colors.textMuted, maxLines = 1)
            }
        },
        modifier = Modifier.width(CELL * weeks + GAP * (weeks - 1)),
    ) { measurables, constraints ->
        val placeables = measurables.map { it.measure(constraints.copy(minWidth = 0)) }
        val height = placeables.maxOfOrNull { it.height } ?: 0
        layout(constraints.maxWidth, height + (GAP * 2).roundToPx()) {
            placeables.forEachIndexed { index, placeable ->
                placeable.place(((CELL + GAP) * kept[index].first).roundToPx(), 0)
            }
        }
    }
}

private val CELL: Dp = 11.dp
private val GAP: Dp = 3.dp
