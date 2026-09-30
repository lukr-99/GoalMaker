package com.goalmaker.app.ui.tally

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.goalmaker.app.R
import com.goalmaker.app.ui.theme.AppTheme
import kotlin.math.max

/*
 * Tally's charts, drawn like the stats screen's bars (docs/design/spec.md): one stacked bar by
 * category, a row of stacked bars, and the legend under them. Bars grow in over the standard
 * duration; with reduce motion on they only fade in, quickly.
 */

/** "3 h 5 min", "45 min" or "2 h", as the connector says it. */
@Composable
fun durationText(minutes: Int): String {
    val hours = minutes / 60
    val rest = minutes % 60
    return when {
        hours == 0 -> stringResource(R.string.tally_minutes, rest)
        rest == 0 -> stringResource(R.string.tally_hours, hours)
        else -> stringResource(R.string.tally_hours_minutes, hours, rest)
    }
}

/** A category's palette color (the area palette's swatch), or the outline for one not in the palette. */
@Composable
fun tallyColor(color: String): Color =
    AppTheme.areaColors.firstOrNull { it.id == color }?.let { Color(it.swatch) } ?: AppTheme.colors.outline

/** A slice's name, or what a removed category is called. */
@Composable
fun sliceName(slice: TallySlice): String = slice.name.ifBlank { stringResource(R.string.tally_removed_category) }

/** Text in tabular figures, so minutes don't jiggle as they change. */
@Composable
fun tabular(style: TextStyle): TextStyle = style.copy(fontFeatureSettings = "tnum")

/** One horizontal bar split by category, the most first, as long as the bar is. */
@Composable
fun TallyStackedBar(slices: List<TallySlice>, modifier: Modifier = Modifier, height: Dp = 16.dp) {
    val shown = rememberShown()
    val reduced = AppTheme.reduceMotion
    val scale = if (reduced) 1f else shown
    val fade = if (reduced) shown else 1f
    val colors = slices.map { tallyColor(it.color) }
    val empty = AppTheme.colors.outline.copy(alpha = 0.3f)
    val description = slices.map { "${sliceName(it)} ${durationText(it.minutes)}" }.joinToString(", ")
    val total = slices.sumOf(TallySlice::minutes)
    Canvas(
        modifier
            .fillMaxWidth()
            .height(height)
            .clip(RoundedCornerShape(height / 2))
            .alpha(fade)
            .semantics { contentDescription = description },
    ) {
        drawRect(empty)
        if (total == 0) return@Canvas
        var x = 0f
        slices.forEachIndexed { index, slice ->
            val width = size.width * scale * slice.minutes / total
            drawRect(colors[index], topLeft = Offset(x, 0f), size = Size(width, size.height))
            x += width
        }
    }
}

/**
 * A stacked bar per day or week, the tallest as tall as the row, each with its label underneath when
 * [labels] has them; a bar with nothing is a short grey stub, as in the stats screen.
 */
@Composable
fun TallyColumns(bars: List<TallyBar>, modifier: Modifier = Modifier, labels: List<String> = emptyList(), height: Dp = 96.dp) {
    val shown = rememberShown()
    val reduced = AppTheme.reduceMotion
    val scale = if (reduced) 1f else shown
    val fade = if (reduced) shown else 1f
    val most = max(1, bars.maxOfOrNull(TallyBar::minutes) ?: 1)
    val empty = AppTheme.colors.outline.copy(alpha = 0.3f)
    Row(
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.Bottom,
        modifier = modifier.fillMaxWidth(),
    ) {
        bars.forEachIndexed { index, bar ->
            val colors = bar.slices.map { tallyColor(it.color) }
            val description = listOf(labels.getOrElse(index) { bar.day.toString() }, durationText(bar.minutes)).joinToString(", ")
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.weight(1f).semantics { contentDescription = description },
            ) {
                Canvas(
                    Modifier
                        .fillMaxWidth()
                        .height(height)
                        .alpha(fade),
                ) {
                    val radius = 4.dp.toPx()
                    val stub = 6.dp.toPx()
                    if (bar.minutes == 0) {
                        drawRoundRect(empty, Offset(0f, size.height - stub), Size(size.width, stub), CornerRadius(radius))
                        return@Canvas
                    }
                    val full = max(stub, size.height * bar.minutes / most) * scale
                    var bottom = size.height
                    bar.slices.forEachIndexed { part, slice ->
                        val tall = full * slice.minutes / bar.minutes
                        drawRect(colors[part], topLeft = Offset(0f, bottom - tall), size = Size(size.width, tall))
                        bottom -= tall
                    }
                }
                if (labels.isNotEmpty()) {
                    Text(
                        labels.getOrElse(index) { "" },
                        style = MaterialTheme.typography.labelSmall,
                        color = AppTheme.colors.textMuted,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

/** A dot, the name and the minutes for each slice. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TallyLegend(slices: List<TallySlice>, modifier: Modifier = Modifier) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
        modifier = modifier.fillMaxWidth(),
    ) {
        slices.forEach { slice ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(10.dp).clip(RoundedCornerShape(5.dp)).background(tallyColor(slice.color)))
                Text(sliceName(slice), style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(start = 6.dp))
                Text(
                    durationText(slice.minutes),
                    style = tabular(MaterialTheme.typography.labelMedium),
                    color = AppTheme.colors.textMuted,
                    modifier = Modifier.padding(start = 4.dp),
                )
            }
        }
    }
}

/** How far a chart has come in, from 0 to 1: grown over the standard duration, or faded in quickly with reduce motion. */
@Composable
private fun rememberShown(): Float {
    val reduced = AppTheme.reduceMotion
    val motion = AppTheme.motion
    val shown = remember { Animatable(0f) }
    LaunchedEffect(Unit) { shown.animateTo(1f, tween(if (reduced) motion.quick else motion.standard)) }
    return shown.value
}
