package com.goalmaker.app.ui.habits

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.outlined.LocalFireDepartment
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.goalmaker.app.R
import com.goalmaker.app.application.planning.HabitItem
import com.goalmaker.app.application.planning.HabitRules
import com.goalmaker.app.ui.theme.AppTheme
import java.time.LocalDate

/**
 * A habit card (the habits prototype, option B), on Today and the Habits screen: the emoji, the name
 * with its streak, where today stands, pips or a bar, the week's dots, the check-in button and the
 * menu. [full] adds how often it runs, the goal it serves, the Not on Today mark and the heatmap (the
 * Habits screen).
 * A long press opens the same menu as the button, where skipping lives; readers get Skip and More as
 * actions, so nothing needs the long press.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun HabitCard(
    row: HabitRow,
    today: LocalDate,
    full: Boolean,
    onCheckIn: () -> Unit,
    onMenu: () -> Unit,
    onSkip: () -> Unit,
    modifier: Modifier = Modifier,
    onClick: () -> Unit = onMenu,
) {
    val habit = row.habit
    val colors = AppTheme.colors
    val more = stringResource(R.string.habits_more, habit.name)
    val skipLabel = stringResource(skipText(habit))
    val canSkip = !habit.archived && row.canCheckIn && !row.skipped && !row.failed
    Column(
        modifier = modifier
            .fillMaxWidth()
            .alpha(if (row.skipped || habit.archived) 0.7f else 1f)
            .background(colors.surface, AppTheme.shapes.card)
            .combinedClickable(onClick = onClick, onLongClick = onMenu, onLongClickLabel = more)
            .semantics {
                customActions = listOfNotNull(
                    CustomAccessibilityAction(skipLabel) { onSkip(); true }.takeIf { canSkip },
                    CustomAccessibilityAction(more) { onMenu(); true },
                )
            }
            .padding(start = 12.dp, end = 0.dp, top = 10.dp, bottom = 10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            EmojiTile(row)
            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                // The card's tap merges the name, the streak and where today stands into one item to read
                // (M6-05); the check-in button and the menu stay their own controls.
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            habit.name,
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.SemiBold,
                            color = if (row.done) colors.textMuted else colors.text,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false),
                        )
                        Flame(row, Modifier.padding(start = 8.dp))
                    }
                    val status = when {
                        habit.archived -> stringResource(R.string.habits_line, cadenceText(habit), stringResource(R.string.habits_archived_line))
                        full -> stringResource(R.string.habits_line, cadenceText(habit), statusText(row))
                        else -> statusText(row)
                    }
                    Text(
                        status,
                        style = MaterialTheme.typography.bodySmall,
                        fontStyle = if (row.skipped) FontStyle.Italic else FontStyle.Normal,
                        color = if (row.isOver || row.failed) colors.danger else colors.textMuted,
                    )
                    if (full) {
                        row.goalTitle?.let { Text(stringResource(R.string.habits_serves, it), style = MaterialTheme.typography.bodySmall, color = colors.textMuted) }
                    }
                }
                if (row.canCheckIn && !row.skipped && !row.failed) HabitProgress(row)
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 6.dp)) {
                    HabitWeekDots(row.dots, today, Modifier.weight(1f, fill = false))
                    if (full && !habit.showOnToday && !habit.archived) NotOnToday(Modifier.padding(start = 8.dp))
                }
            }
            if (!habit.archived) HabitCheckInButton(row, onCheckIn)
            IconButton(onClick = onMenu) {
                Icon(Icons.Outlined.MoreVert, contentDescription = more, tint = colors.textMuted)
            }
        }
        // The Habits screen keeps each habit's map of the months behind it (story 42).
        if (full && row.heat.isNotEmpty()) HabitHeatmap(row, Modifier.padding(top = 10.dp, end = 12.dp))
    }
}

// The emoji on a soft tile, or the name's first letter when the habit has none.
@Composable
private fun EmojiTile(row: HabitRow) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(40.dp)
            .background(AppTheme.colors.surfaceVariant, RoundedCornerShape(12.dp))
            .clearAndSetSemantics {},
    ) {
        val emoji = row.habit.emoji
        if (emoji != null) {
            // A picture on the tile, so it keeps its size when the system's text grows.
            Text(emoji, fontSize = with(LocalDensity.current) { 20.dp.toSp() })
        } else {
            Text(
                row.habit.name.take(1).uppercase(),
                style = MaterialTheme.typography.titleMedium,
                color = AppTheme.colors.accent,
            )
        }
    }
}

// The streak as a flame and its number, muted before the first period is met.
@Composable
private fun Flame(row: HabitRow, modifier: Modifier = Modifier) {
    val colors = AppTheme.colors
    val lit = row.streak > 0
    val description = streakText(row)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier.clearAndSetSemantics { if (description != null) contentDescription = description },
    ) {
        Icon(
            if (lit) Icons.Filled.LocalFireDepartment else Icons.Outlined.LocalFireDepartment,
            contentDescription = null,
            tint = if (lit) colors.accent else colors.textMuted,
            modifier = Modifier.size(16.dp),
        )
        Text(
            row.streak.toString(),
            style = AppTheme.type.number.merge(MaterialTheme.typography.labelMedium),
            fontWeight = if (lit) FontWeight.Bold else FontWeight.Normal,
            color = if (lit) colors.accent else colors.textMuted,
            modifier = Modifier.padding(start = 2.dp),
        )
    }
}

// A habit kept off Today says so on the Habits screen (docs/habits.md).
@Composable
private fun NotOnToday(modifier: Modifier = Modifier) {
    Text(
        stringResource(R.string.habits_not_on_today),
        style = MaterialTheme.typography.labelSmall,
        color = AppTheme.colors.textMuted,
        modifier = modifier
            .border(1.dp, AppTheme.colors.outline.copy(alpha = 0.6f), RoundedCornerShape(50))
            .padding(horizontal = 8.dp, vertical = 2.dp),
    )
}

/** What failing a habit's period is called: today, this week or this month. */
internal fun failText(habit: HabitItem): Int = when (habit.cadence) {
    HabitRules.PER_WEEK -> R.string.habits_fail_week
    HabitRules.PER_MONTH -> R.string.habits_fail_month
    else -> R.string.habits_fail_day
}

/** What skipping a habit's period is called: today, this week or this month. */
internal fun skipText(habit: HabitItem): Int = when (habit.cadence) {
    HabitRules.PER_WEEK -> R.string.habits_skip_week
    HabitRules.PER_MONTH -> R.string.habits_skip_month
    else -> R.string.habits_skip_day
}
