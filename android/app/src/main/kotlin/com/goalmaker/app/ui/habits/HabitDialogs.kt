package com.goalmaker.app.ui.habits

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.ArrowDropDown
import androidx.compose.material.icons.outlined.Remove
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.goalmaker.app.R
import com.goalmaker.app.application.planning.GoalItem
import com.goalmaker.app.application.planning.HabitDraft
import com.goalmaker.app.application.planning.HabitItem
import com.goalmaker.app.application.planning.HabitRules
import com.goalmaker.app.ui.components.ChoiceChip
import com.goalmaker.app.ui.components.EmojiField
import com.goalmaker.app.ui.theme.AppTheme
import java.time.DayOfWeek
import java.time.format.TextStyle
import kotlinx.coroutines.launch

/**
 * Adds or edits a habit (spec, stories 36, 37 and 32): its name and emoji, how often it runs, how it is
 * measured, whether its number is something to reach or a limit to stay under, and the goal it serves.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun HabitDialog(
    habit: HabitItem,
    goals: List<GoalItem>,
    onSave: suspend (HabitDraft) -> Boolean,
    onDelete: (() -> Unit)?,
    onDismiss: () -> Unit,
) {
    var name by remember { mutableStateOf(habit.name) }
    var emoji by remember { mutableStateOf(habit.emoji.orEmpty()) }
    var cadence by remember { mutableStateOf(habit.cadence) }
    var weekdays by remember { mutableIntStateOf(habit.weekdays ?: WORKDAYS) }
    var times by remember { mutableIntStateOf(habit.times ?: 3) }
    var measure by remember { mutableStateOf(habit.measure) }
    var direction by remember { mutableStateOf(habit.direction) }
    var target by remember { mutableStateOf(habit.target?.let(::plainAmount).orEmpty()) }
    var unit by remember { mutableStateOf(habit.unit.orEmpty()) }
    var goalId by remember { mutableStateOf(habit.goalId) }
    var refused by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val locale = LocalConfiguration.current.locales[0]
    val most = if (cadence == HabitRules.PER_MONTH) 31 else 7
    // Only a day can be a limit (supabase/migrations/0014_habit_limits.sql).
    val onDays = cadence == HabitRules.DAILY || cadence == HabitRules.WEEKDAYS

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(if (habit.id.isEmpty()) R.string.habits_new else R.string.habits_edit)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.verticalScroll(rememberScrollState())) {
                OutlinedTextField(
                    value = name,
                    onValueChange = {
                        name = it.take(100)
                        refused = false
                    },
                    label = { Text(stringResource(R.string.habits_name)) },
                    isError = refused && name.isBlank(),
                    singleLine = true,
                )
                EmojiField(emoji, onChange = { emoji = it })
                Label(stringResource(R.string.habits_cadence))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(
                        HabitRules.DAILY to R.string.habits_cadence_daily,
                        HabitRules.WEEKDAYS to R.string.habits_cadence_weekdays,
                        HabitRules.PER_WEEK to R.string.habits_cadence_per_week,
                        HabitRules.PER_MONTH to R.string.habits_cadence_per_month,
                    ).forEach { (id, label) ->
                        ChoiceChip(
                            selected = cadence == id,
                            onClick = {
                                cadence = id
                                if (id == HabitRules.PER_WEEK) times = times.coerceAtMost(7)
                                refused = false
                            },
                            label = stringResource(label),
                        )
                    }
                }
                when (cadence) {
                    HabitRules.WEEKDAYS -> FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        DayOfWeek.entries.forEach { day ->
                            val bit = 1 shl (day.value - 1)
                            ChoiceChip(
                                selected = weekdays and bit != 0,
                                onClick = {
                                    weekdays = weekdays xor bit
                                    refused = false
                                },
                                label = day.getDisplayName(TextStyle.SHORT, locale),
                            )
                        }
                    }
                    HabitRules.PER_WEEK, HabitRules.PER_MONTH -> Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { times = (times - 1).coerceAtLeast(1) }, enabled = times > 1) {
                            Icon(Icons.Outlined.Remove, contentDescription = stringResource(R.string.habits_fewer))
                        }
                        Text(
                            if (cadence == HabitRules.PER_WEEK) {
                                pluralStringResource(R.plurals.habits_times_week, times, times)
                            } else {
                                pluralStringResource(R.plurals.habits_times_month, times, times)
                            },
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.weight(1f),
                        )
                        IconButton(onClick = { times = (times + 1).coerceAtMost(most) }, enabled = times < most) {
                            Icon(Icons.Outlined.Add, contentDescription = stringResource(R.string.habits_more_times))
                        }
                    }
                }
                Label(stringResource(R.string.habits_measure))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(
                        HabitRules.CHECK to R.string.habits_measure_check,
                        HabitRules.COUNT to R.string.habits_measure_count,
                        HabitRules.AMOUNT to R.string.habits_measure_amount,
                    ).forEach { (id, label) ->
                        ChoiceChip(
                            selected = measure == id,
                            onClick = {
                                measure = id
                                refused = false
                            },
                            label = stringResource(label),
                        )
                    }
                }
                val limit = onDays && direction == HabitRules.AT_MOST
                if (onDays) {
                    Label(stringResource(R.string.habits_direction))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(
                            HabitRules.AT_LEAST to R.string.habits_at_least,
                            HabitRules.AT_MOST to R.string.habits_at_most,
                        ).forEach { (id, label) ->
                            ChoiceChip(
                                selected = direction == id,
                                onClick = {
                                    direction = id
                                    refused = false
                                },
                                label = stringResource(label),
                            )
                        }
                    }
                    if (limit) {
                        Text(
                            stringResource(
                                if (measure == HabitRules.CHECK) R.string.habits_limit_hint_check else R.string.habits_limit_hint,
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = AppTheme.colors.textMuted,
                        )
                    }
                }
                if (measure != HabitRules.CHECK) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = target,
                            onValueChange = {
                                target = it
                                refused = false
                            },
                            label = { Text(stringResource(R.string.habits_target)) },
                            isError = refused && (parseAmount(target) ?: 0.0) <= 0.0,
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            modifier = Modifier.weight(1f),
                        )
                        OutlinedTextField(
                            value = unit,
                            onValueChange = { unit = it.take(20) },
                            label = { Text(stringResource(R.string.habits_unit)) },
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
                if (goals.isNotEmpty()) GoalField(goals.firstOrNull { it.id == goalId }, goals, onPick = { goalId = it })
                if (refused) {
                    Text(stringResource(R.string.habits_invalid), style = MaterialTheme.typography.bodySmall, color = AppTheme.colors.danger)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val draft = HabitDraft(
                    name = name,
                    startsOn = habit.startsOn,
                    cadence = cadence,
                    weekdays = weekdays.takeIf { cadence == HabitRules.WEEKDAYS && it != 0 },
                    times = times.takeIf { cadence == HabitRules.PER_WEEK || cadence == HabitRules.PER_MONTH },
                    measure = measure,
                    direction = if (onDays) direction else HabitRules.AT_LEAST,
                    target = parseAmount(target),
                    unit = unit,
                    emoji = emoji,
                    goalId = goalId,
                )
                scope.launch { if (onSave(draft)) onDismiss() else refused = true }
            }) { Text(stringResource(R.string.habits_save)) }
        },
        dismissButton = {
            Row {
                onDelete?.let { delete ->
                    TextButton(onClick = {
                        delete()
                        onDismiss()
                    }) { Text(stringResource(R.string.habits_delete), color = AppTheme.colors.danger) }
                }
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.habits_cancel)) }
            }
        },
    )
}

@Composable
private fun GoalField(goal: GoalItem?, goals: List<GoalItem>, onPick: (String?) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Column {
        Label(stringResource(R.string.habits_goal))
        Box {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .clickable { open = true },
            ) {
                Text(goal?.let(::goalName) ?: stringResource(R.string.habits_no_goal), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                Icon(Icons.Outlined.ArrowDropDown, contentDescription = null)
            }
            DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                DropdownMenuItem(text = { Text(stringResource(R.string.habits_no_goal)) }, onClick = {
                    open = false
                    onPick(null)
                })
                goals.forEach { choice ->
                    DropdownMenuItem(text = { Text(goalName(choice)) }, onClick = {
                        open = false
                        onPick(choice.id)
                    })
                }
            }
        }
        Text(stringResource(R.string.habits_goal_hint), style = MaterialTheme.typography.bodySmall, color = AppTheme.colors.textMuted)
    }
}

/** Adds an amount to today's value of a count or amount habit. */
@Composable
internal fun AmountDialog(habit: HabitItem, onLog: (Double) -> Unit, onDismiss: () -> Unit) {
    var text by remember { mutableStateOf("") }
    val amount = parseAmount(text)?.takeIf { it > 0.0 }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.habits_log_title, habit.name)) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                label = { Text(stringResource(R.string.habits_log_amount)) },
                suffix = habit.unit?.let { unit -> { Text(unit) } },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.focusRequester(focus).padding(top = 4.dp),
            )
        },
        confirmButton = {
            TextButton(enabled = amount != null, onClick = {
                amount?.let(onLog)
                onDismiss()
            }) { Text(stringResource(R.string.habits_log_add)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.habits_cancel)) } },
    )
}

@Composable
private fun Label(text: String) {
    Text(text, style = MaterialTheme.typography.titleSmall, color = AppTheme.colors.accent)
}

private fun goalName(goal: GoalItem) = listOfNotNull(goal.emoji, goal.title).joinToString(" ")
