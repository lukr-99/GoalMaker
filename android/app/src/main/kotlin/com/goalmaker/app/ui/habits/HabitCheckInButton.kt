package com.goalmaker.app.ui.habits

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Undo
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.goalmaker.app.R
import com.goalmaker.app.application.planning.HabitRules
import com.goalmaker.app.ui.theme.AppTheme

/**
 * A habit card's check-in button (the habits prototype, option B). A check toggles, a count adds one
 * ("+1"), an amount asks for its value. Done, it fills with the accent and turns round; skipped, it is a
 * dashed outline whose tap undoes the skip. It rests while the habit is paused or not due today.
 */
@Composable
fun HabitCheckInButton(row: HabitRow, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val habit = row.habit
    val colors = AppTheme.colors
    val reduceMotion = AppTheme.reduceMotion
    val checkMeasure = habit.measure == HabitRules.CHECK
    val on = row.done
    val corner by animateDpAsState(
        targetValue = if (on) 24.dp else 16.dp,
        animationSpec = if (reduceMotion) tween(0) else spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMediumLow),
        label = "check-in corner",
    )
    val fill by animateColorAsState(
        targetValue = when {
            row.skipped -> colors.surface.copy(alpha = 0f)
            on -> colors.accent
            else -> colors.surfaceVariant
        },
        animationSpec = tween(if (reduceMotion) 0 else AppTheme.motion.standard),
        label = "check-in fill",
    )
    val content = if (on) colors.onAccent else colors.text
    val label = when {
        row.skipped -> stringResource(R.string.habits_unskip_on, habit.name)
        checkMeasure && row.value >= 1.0 -> stringResource(R.string.habits_take_back, habit.name)
        checkMeasure -> stringResource(R.string.habits_check_in, habit.name)
        habit.measure == HabitRules.COUNT -> stringResource(R.string.habits_add_one, habit.name)
        else -> stringResource(R.string.habits_log_on, habit.name)
    }
    val status = statusText(row)
    val shape = RoundedCornerShape(corner)
    val outline = colors.outline
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(width = 52.dp, height = 48.dp)
            .alpha(if (row.canCheckIn) 1f else 0.5f)
            .clip(shape)
            .background(fill, shape)
            .drawBehind {
                if (row.skipped) {
                    val width = 2.dp.toPx()
                    drawRoundRect(
                        outline,
                        topLeft = androidx.compose.ui.geometry.Offset(width / 2, width / 2),
                        size = androidx.compose.ui.geometry.Size(size.width - width, size.height - width),
                        cornerRadius = CornerRadius(corner.toPx()),
                        style = Stroke(width, pathEffect = PathEffect.dashPathEffect(floatArrayOf(width * 2.5f, width * 2f))),
                    )
                }
            }
            .clickable(enabled = row.canCheckIn, role = Role.Button, onClick = onClick)
            .semantics {
                contentDescription = label
                stateDescription = status
            },
    ) {
        when {
            row.skipped -> Icon(Icons.AutoMirrored.Outlined.Undo, contentDescription = null, tint = colors.textMuted)
            !checkMeasure && habit.measure == HabitRules.COUNT ->
                Text("+1", style = AppTheme.type.number.merge(MaterialTheme.typography.titleSmall), fontWeight = FontWeight.Bold, color = content)
            on && checkMeasure -> Icon(Icons.Outlined.Check, contentDescription = null, tint = content)
            row.isLimit && checkMeasure && row.value >= 1.0 -> Icon(Icons.Outlined.Check, contentDescription = null, tint = colors.danger)
            on -> Icon(Icons.Outlined.Check, contentDescription = null, tint = content)
            else -> Icon(Icons.Outlined.Add, contentDescription = null, tint = content)
        }
    }
}
