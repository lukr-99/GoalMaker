package com.goalmaker.app.ui.habits

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import com.goalmaker.app.application.planning.HabitRules
import com.goalmaker.app.ui.theme.AppTheme
import kotlin.math.ceil
import kotlin.math.max

/**
 * How far today has got, under a habit's name (the habits prototype, option B): a pip for each glass
 * of a small count or each day a weekly habit needs, a bar for an amount or a big count. A limit's
 * pips past the line turn to the danger colour. A check habit has neither; its button says it all.
 * The status line above says the same in words, so readers skip this.
 */
@Composable
fun HabitProgress(row: HabitRow, modifier: Modifier = Modifier) {
    val habit = row.habit
    val weekly = habit.cadence == HabitRules.PER_WEEK || habit.cadence == HabitRules.PER_MONTH
    val pips = when {
        weekly -> (habit.times ?: 1).takeIf { it <= MAX_PIPS }?.let { times -> List(times) { it < row.met } to 0 }
        habit.measure == HabitRules.COUNT -> {
            val target = ceil(habit.target ?: 1.0).toInt()
            val count = max(target, ceil(row.value).toInt())
            if (count <= MAX_PIPS) List(count) { it < row.value } to target else null
        }
        else -> null
    }
    val shown = Modifier.fillMaxWidth().padding(top = 4.dp).then(modifier).clearAndSetSemantics {}
    when {
        pips != null -> Pips(pips.first, overFrom = if (row.isLimit) pips.second else Int.MAX_VALUE, modifier = shown)
        habit.measure == HabitRules.CHECK && !weekly -> Unit
        else -> Bar((row.ring ?: 0.0).toFloat(), over = row.isOver, modifier = shown)
    }
}

@Composable
private fun Pips(on: List<Boolean>, overFrom: Int, modifier: Modifier) {
    val colors = AppTheme.colors
    Row(horizontalArrangement = Arrangement.spacedBy(3.dp), modifier = modifier) {
        on.forEachIndexed { index, filled ->
            val color = when {
                filled && index >= overFrom -> colors.danger
                filled -> colors.accent
                else -> colors.outline.copy(alpha = 0.3f)
            }
            Box(Modifier.weight(1f).height(6.dp).clip(CircleShape).background(color))
        }
    }
}

@Composable
private fun Bar(fraction: Float, over: Boolean, modifier: Modifier) {
    val reduceMotion = AppTheme.reduceMotion
    val shown by animateFloatAsState(
        targetValue = fraction.coerceIn(0f, 1f),
        animationSpec = if (reduceMotion) tween(0) else spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessLow),
        label = "habit bar",
    )
    Box(modifier.height(6.dp).clip(CircleShape).background(AppTheme.colors.outline.copy(alpha = 0.3f))) {
        Box(
            Modifier
                .fillMaxHeight()
                .fillMaxWidth(shown)
                .clip(CircleShape)
                .background(if (over) AppTheme.colors.danger else AppTheme.colors.accent),
        )
    }
}

// More than this many pips read as a bar.
private const val MAX_PIPS = 12
