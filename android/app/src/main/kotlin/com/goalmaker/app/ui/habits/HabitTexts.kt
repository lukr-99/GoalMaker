package com.goalmaker.app.ui.habits

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.goalmaker.app.R
import com.goalmaker.app.application.planning.HabitItem
import com.goalmaker.app.application.planning.HabitRules
import java.text.NumberFormat
import java.time.DayOfWeek
import java.time.format.TextStyle
import java.util.Locale

/** How often a habit runs: "Every day", "Mon, Wed, Fri", "3 times a week". */
@Composable
internal fun cadenceText(habit: HabitItem): String {
    val locale = LocalConfiguration.current.locales[0]
    return when (habit.cadence) {
        HabitRules.WEEKDAYS -> when (val mask = habit.weekdays ?: 0) {
            WORKDAYS -> stringResource(R.string.habits_workdays)
            WEEKEND -> stringResource(R.string.habits_weekend)
            else -> DayOfWeek.entries.filter { mask and (1 shl (it.value - 1)) != 0 }
                .joinToString(", ") { it.getDisplayName(TextStyle.SHORT, locale) }
        }
        HabitRules.PER_WEEK -> (habit.times ?: 1).let { pluralStringResource(R.plurals.habits_times_week, it, it) }
        HabitRules.PER_MONTH -> (habit.times ?: 1).let { pluralStringResource(R.plurals.habits_times_month, it, it) }
        else -> stringResource(R.string.habits_cadence_daily)
    }
}

/** "5-day streak", "3-week streak", or null before the first period is met. */
@Composable
internal fun streakText(row: HabitRow): String? {
    val streak = row.streak.takeIf { it > 0 } ?: return null
    return when (row.habit.cadence) {
        HabitRules.PER_WEEK -> pluralStringResource(R.plurals.habits_streak_weeks, streak, streak)
        HabitRules.PER_MONTH -> pluralStringResource(R.plurals.habits_streak_months, streak, streak)
        else -> pluralStringResource(R.plurals.habits_streak_days, streak, streak)
    }
}

/** Where today stands: "Paused", "Skipped", "4 of 8 glasses", "2 of 3 this week", "Done", "Not yet". */
@Composable
internal fun statusText(row: HabitRow): String {
    val habit = row.habit
    val locale = LocalConfiguration.current.locales[0]
    return when {
        row.paused -> stringResource(R.string.habits_paused)
        row.skipped -> stringResource(R.string.habits_skipped)
        habit.cadence == HabitRules.PER_WEEK -> pluralStringResource(R.plurals.habits_met_week, habit.times ?: 1, row.met, habit.times ?: 1)
        habit.cadence == HabitRules.PER_MONTH -> pluralStringResource(R.plurals.habits_met_month, habit.times ?: 1, row.met, habit.times ?: 1)
        row.ring == null -> stringResource(R.string.habits_not_due)
        habit.measure == HabitRules.CHECK -> stringResource(if (row.done) R.string.habits_done else R.string.habits_not_yet)
        else -> {
            val value = amountText(row.value, locale)
            val target = amountText(habit.target ?: 0.0, locale)
            habit.unit?.let { stringResource(R.string.habits_value_unit, value, target, it) } ?: stringResource(R.string.habits_value, value, target)
        }
    }
}

internal fun amountText(value: Double, locale: Locale): String =
    NumberFormat.getNumberInstance(locale).apply { maximumFractionDigits = 2 }.format(value)

// A target as it was typed: no ".0" on whole numbers.
internal fun plainAmount(value: Double): String = if (value % 1.0 == 0.0) value.toLong().toString() else value.toString()

// "5", "5.5" or "5,5"; null when it isn't a number.
internal fun parseAmount(text: String): Double? = text.trim().replace(',', '.').toDoubleOrNull()?.takeIf(Double::isFinite)

// Monday to Friday, and Saturday with Sunday, as weekday masks.
internal const val WORKDAYS = 31
internal const val WEEKEND = 96
