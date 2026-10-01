package com.goalmaker.app.ui.habits

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Repeat
import androidx.compose.material.icons.outlined.TrackChanges
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.goalmaker.app.R
import com.goalmaker.app.application.planning.HabitDraft
import com.goalmaker.app.application.planning.HabitItem
import com.goalmaker.app.application.planning.HabitRules
import com.goalmaker.app.domain.composer.SpanKind
import com.goalmaker.app.ui.composer.ComposerChip

/**
 * The bottom bar's preview on Habits (docs/composer.md): how often ("Every day", "2 times a week",
 * "Mon, Wed, Fri") and how much ("8 glasses a day", "40 min", "A simple check"). Nothing while the
 * line is empty.
 */
@Composable
internal fun habitLineChips(line: String, draft: HabitDraft): List<ComposerChip> {
    if (line.isBlank()) return emptyList()
    val locale = LocalConfiguration.current.locales[0]
    val onDays = draft.cadence == HabitRules.DAILY || draft.cadence == HabitRules.WEEKDAYS
    val target = draft.target ?: 0.0
    val unit = draft.unit
    val measure = when {
        draft.measure == HabitRules.CHECK -> stringResource(R.string.bar_habit_check)
        unit == null -> target.toInt().let { times ->
            if (onDays) pluralStringResource(R.plurals.bar_habit_times_day, times, times) else pluralStringResource(R.plurals.bar_habit_times, times, times)
        }
        onDays -> stringResource(R.string.bar_habit_amount_day, amountText(target, locale), unit)
        else -> stringResource(R.string.bar_habit_amount, amountText(target, locale), unit)
    }
    return listOf(
        chip(cadenceText(draft.asNewHabit()), Icons.Outlined.Repeat),
        chip(measure, if (draft.measure == HabitRules.CHECK) Icons.Outlined.CheckCircle else Icons.Outlined.TrackChanges),
    )
}

/** A draft as a habit not saved yet (an empty id), for the habit form and the habit texts. */
internal fun HabitDraft.asNewHabit(): HabitItem = HabitItem(
    id = "",
    name = name,
    startsOn = startsOn,
    cadence = cadence,
    weekdays = weekdays,
    times = times,
    measure = measure,
    target = target,
    direction = direction,
    unit = unit,
    emoji = emoji,
    goalId = goalId,
    showOnToday = showOnToday,
)

private fun chip(label: String, icon: ImageVector) = ComposerChip(SpanKind.REPEAT, label, null, false, null, emptyList(), icon = icon)
