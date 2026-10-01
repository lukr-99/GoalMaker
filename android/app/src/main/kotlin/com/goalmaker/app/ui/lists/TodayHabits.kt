package com.goalmaker.app.ui.lists

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.DonutLarge
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.goalmaker.app.R
import com.goalmaker.app.ui.habits.AllDoneCard
import com.goalmaker.app.ui.habits.HabitCard
import com.goalmaker.app.ui.habits.HabitRow
import com.goalmaker.app.ui.habits.HideDoneChip
import com.goalmaker.app.ui.theme.AppTheme
import java.time.LocalDate

/**
 * Today's habits half (the habits prototype, option B's cards): how many are left with Hide done, the
 * all done card, and a card for each habit on Today (contracts/vectors/habits.json, onToday).
 */
internal fun LazyListScope.habits(
    state: ListsUiState,
    today: LocalDate,
    viewModel: ListsViewModel,
    onOpenHabits: () -> Unit,
    onCheckIn: (HabitRow) -> Unit,
    onMenu: (HabitRow) -> Unit,
) {
    if (state.habits.isEmpty()) {
        item(key = "habits-none") {
            Column(Modifier.padding(top = 8.dp)) {
                Text(
                    stringResource(R.string.habits_today_none),
                    style = MaterialTheme.typography.bodyLarge,
                    color = AppTheme.colors.textMuted,
                    modifier = Modifier.padding(8.dp),
                )
                TextButton(onClick = onOpenHabits) { Text(stringResource(R.string.habits_open)) }
            }
        }
        return
    }
    item(key = "habits-bar") {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(top = 4.dp, start = 4.dp)) {
            val label = if (state.habitsLeft > 0) {
                pluralStringResource(R.plurals.habits_left_today, state.habitsLeft, state.habitsLeft)
            } else {
                stringResource(R.string.habits_all_done_label)
            }
            Text(
                AppTheme.headline(label).uppercase(LocalConfiguration.current.locales[0]),
                style = MaterialTheme.typography.labelMedium,
                color = AppTheme.colors.accent,
                modifier = Modifier.weight(1f).semantics { heading() },
            )
            HideDoneChip(state.hideDoneHabits, viewModel::setHideDoneHabits)
        }
    }
    if (state.habitsAllDone) item(key = "habits-all-done") { AllDoneCard(Modifier.animateItem()) }
    items(state.shownHabits, key = { "habit-" + it.habit.id }) { row ->
        HabitCard(
            row = row,
            today = today,
            full = false,
            onCheckIn = { onCheckIn(row) },
            onMenu = { onMenu(row) },
            onSkip = { viewModel.skipHabit(row.habit.id, true) },
            modifier = Modifier.animateItem(),
        )
    }
}

/** Under Today's tasks: how many habits are left, with their emoji, a tap from the habits half. */
@Composable
internal fun HabitsNudge(left: List<HabitRow>, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val names = left.joinToString(" ") { it.habit.emoji ?: it.habit.name }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .border(1.dp, AppTheme.colors.outline.copy(alpha = 0.6f), AppTheme.shapes.row)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        Icon(Icons.Outlined.DonutLarge, contentDescription = null, tint = AppTheme.colors.accent)
        Text(
            pluralStringResource(R.plurals.habits_nudge, left.size, left.size, names),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f),
        )
        Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, contentDescription = null, tint = AppTheme.colors.textMuted)
    }
}
