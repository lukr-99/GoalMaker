package com.goalmaker.app.ui.habits

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.automirrored.outlined.Redo
import androidx.compose.material.icons.automirrored.outlined.Undo
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Archive
import androidx.compose.material.icons.automirrored.outlined.Backspace
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.PauseCircle
import androidx.compose.material.icons.outlined.PlayCircle
import androidx.compose.material.icons.outlined.Unarchive
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.goalmaker.app.R
import com.goalmaker.app.application.planning.HabitRules
import com.goalmaker.app.ui.theme.AppTheme

/**
 * Everything a habit can do besides its button, as a sheet: the card's menu and its long press open it
 * (the habits prototype). Check in or add one, log an amount, skip today (or this week) or undo the
 * skip, fail today (or this week) or undo the fail, clear today, pause or resume; the Habits screen adds edit, archive and delete, and Today a way
 * to the Habits screen. Each choice closes the sheet first.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HabitSheet(
    row: HabitRow,
    onDismiss: () -> Unit,
    onCheckIn: () -> Unit,
    onLog: () -> Unit,
    onSkip: (Boolean) -> Unit,
    onFail: (Boolean) -> Unit,
    onClear: () -> Unit,
    onPause: (() -> Unit)? = null,
    onResume: (() -> Unit)? = null,
    onEdit: (() -> Unit)? = null,
    onArchive: ((Boolean) -> Unit)? = null,
    onDelete: (() -> Unit)? = null,
    onOpenHabits: (() -> Unit)? = null,
) {
    val habit = row.habit
    ModalBottomSheet(onDismissRequest = onDismiss) {
        // It scrolls, so at the largest text sizes the last choices stay in reach.
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(start = 24.dp, end = 24.dp, bottom = 32.dp),
        ) {
            Text(
                listOfNotNull(habit.emoji, habit.name).joinToString(" "),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.semantics { heading() },
            )
            Text(
                listOfNotNull(statusText(row), streakText(row)).joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = if (row.isOver || row.failed) AppTheme.colors.danger else AppTheme.colors.textMuted,
                modifier = Modifier.padding(top = 2.dp, bottom = 8.dp),
            )

            @Composable
            fun Choice(icon: ImageVector, label: String, danger: Boolean = false, action: () -> Unit) = SheetChoice(icon, label, danger) {
                onDismiss()
                action()
            }

            val active = !habit.archived && row.canCheckIn
            if (active && !row.skipped) {
                when (habit.measure) {
                    HabitRules.CHECK -> if (row.value >= 1.0) {
                        Choice(Icons.AutoMirrored.Outlined.Undo, stringResource(R.string.habits_menu_take_back), action = onCheckIn)
                    } else {
                        Choice(Icons.Outlined.Check, stringResource(R.string.habits_menu_check_in), action = onCheckIn)
                    }
                    HabitRules.COUNT -> {
                        Choice(Icons.Outlined.Add, stringResource(R.string.habits_menu_add_one), action = onCheckIn)
                        Choice(Icons.Outlined.Edit, stringResource(R.string.habits_log), action = onLog)
                    }
                    else -> Choice(Icons.Outlined.Add, stringResource(R.string.habits_log), action = onLog)
                }
            }
            if (active) {
                if (row.skipped) {
                    Choice(Icons.AutoMirrored.Outlined.Undo, stringResource(R.string.habits_unskip)) { onSkip(false) }
                } else {
                    Choice(Icons.AutoMirrored.Outlined.Redo, stringResource(skipText(habit))) { onSkip(true) }
                }
                if (row.failed) {
                    Choice(Icons.AutoMirrored.Outlined.Undo, stringResource(R.string.habits_unfail)) { onFail(false) }
                } else if (!row.skipped) {
                    Choice(Icons.Outlined.Close, stringResource(failText(habit))) { onFail(true) }
                }
            }
            if (!habit.archived && row.value > 0.0) Choice(Icons.AutoMirrored.Outlined.Backspace, stringResource(R.string.habits_clear), action = onClear)
            if (!habit.archived) {
                if (row.paused) {
                    onResume?.let { Choice(Icons.Outlined.PlayCircle, stringResource(R.string.habits_resume), action = it) }
                } else {
                    onPause?.let { Choice(Icons.Outlined.PauseCircle, stringResource(R.string.habits_pause), action = it) }
                }
            }
            onEdit?.let { Choice(Icons.Outlined.Edit, stringResource(R.string.habits_edit_menu), action = it) }
            onOpenHabits?.let { Choice(Icons.AutoMirrored.Outlined.OpenInNew, stringResource(R.string.habits_open), action = it) }
            onArchive?.let { archive ->
                if (habit.archived) {
                    Choice(Icons.Outlined.Unarchive, stringResource(R.string.habits_unarchive)) { archive(false) }
                } else {
                    Choice(Icons.Outlined.Archive, stringResource(R.string.habits_archive)) { archive(true) }
                }
            }
            onDelete?.let { Choice(Icons.Outlined.Delete, stringResource(R.string.habits_delete), danger = true, action = it) }
        }
    }
}

@Composable
private fun SheetChoice(icon: ImageVector, label: String, danger: Boolean, onClick: () -> Unit) {
    val color = if (danger) AppTheme.colors.danger else AppTheme.colors.text
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(role = Role.Button, onClick = onClick)
            .padding(vertical = 14.dp),
    ) {
        Icon(icon, contentDescription = null, tint = if (danger) color else AppTheme.colors.textMuted, modifier = Modifier.size(20.dp))
        Text(label, style = MaterialTheme.typography.bodyLarge, color = color)
    }
}
