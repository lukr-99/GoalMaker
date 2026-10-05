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

/**
 * How often a habit runs: "Every day", "Mon, Wed, Fri", "3 times a week". A weekly or monthly check
 * limit says how many days it may have: "At most 2 days a week", or "Not once a month" for 0. A
 * weekly or monthly count or amount limit is "Every week": its number is in the limit text.
 */
@Composable
internal fun cadenceText(habit: HabitItem): String {
    val locale = LocalConfiguration.current.locales[0]
    if (HabitRules.isLimit(habit) && HabitRules.isPeriodic(habit)) {
        val week = habit.cadence == HabitRules.PER_WEEK
        if (habit.measure != HabitRules.CHECK) {
            return stringResource(if (week) R.string.habits_every_week else R.string.habits_every_month)
        }
        val times = habit.times ?: 0
        return if (times == 0) {
            stringResource(if (week) R.string.habits_not_once_week else R.string.habits_not_once_month)
        } else {
            pluralStringResource(if (week) R.plurals.habits_at_most_days_week else R.plurals.habits_at_most_days_month, times, times)
        }
    }
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

/** Where today stands: "Paused", "Failed", "4 of 8 glasses", "1 of at most 2 snacks", "Done", "Not yet". */
@Composable
internal fun statusText(row: HabitRow): String {
    val habit = row.habit
    val locale = LocalConfiguration.current.locales[0]
    return when {
        row.paused -> stringResource(R.string.habits_paused)
        row.skipped -> stringResource(R.string.habits_skipped)
        row.failed -> stringResource(R.string.habits_failed)
        row.isLimit && HabitRules.isPeriodic(habit) -> periodLimitText(row, locale)
        habit.cadence == HabitRules.PER_WEEK -> pluralStringResource(R.plurals.habits_met_week, habit.times ?: 1, row.met, habit.times ?: 1)
        habit.cadence == HabitRules.PER_MONTH -> pluralStringResource(R.plurals.habits_met_month, habit.times ?: 1, row.met, habit.times ?: 1)
        row.ring == null -> stringResource(R.string.habits_not_due)
        // A day other than today (the calendar's) says what happened without "today".
        row.isLimit && habit.measure == HabitRules.CHECK -> stringResource(
            when {
                row.value >= 1.0 -> if (row.onDay) R.string.habits_over_on_day else R.string.habits_over_today
                else -> if (row.onDay) R.string.habits_none_on_day else R.string.habits_none_today
            },
        )
        habit.measure == HabitRules.CHECK -> stringResource(
            when {
                row.done -> if (row.onDay) R.string.habits_done_on_day else R.string.habits_done
                else -> if (row.onDay) R.string.habits_not_yet_on_day else R.string.habits_not_yet
            },
        )
        else -> {
            val value = amountText(row.value, locale)
            val target = amountText(habit.target ?: 0.0, locale)
            val unit = habit.unit
            when {
                row.isLimit && unit != null ->
                    stringResource(if (row.onDay) R.string.habits_limit_unit_on_day else R.string.habits_limit_unit, value, target, unit)
                row.isLimit -> stringResource(if (row.onDay) R.string.habits_limit_on_day else R.string.habits_limit, value, target)
                unit != null ->
                    stringResource(if (row.onDay) R.string.habits_value_unit_on_day else R.string.habits_value_unit, value, target, unit)
                else -> stringResource(if (row.onDay) R.string.habits_value_on_day else R.string.habits_value, value, target)
            }
        }
    }
}

/**
 * A weekly or monthly limit: "1 of at most 2 this week", "3 of at most 5 drinks this month", or for a
 * limit of 0, "None this week" and "Over the line this week", like a daily check limit.
 */
@Composable
private fun periodLimitText(row: HabitRow, locale: Locale): String {
    val habit = row.habit
    val value = amountText(row.value, locale)
    val most = amountText(HabitRules.limit(habit), locale)
    val unit = habit.unit?.takeIf { habit.measure != HabitRules.CHECK }
    val week = habit.cadence == HabitRules.PER_WEEK
    return when {
        HabitRules.limit(habit) <= 0.0 -> stringResource(
            when {
                row.value > 0.0 -> if (week) R.string.habits_over_week else R.string.habits_over_month
                else -> if (week) R.string.habits_none_week else R.string.habits_none_month
            },
        )
        unit != null -> stringResource(if (week) R.string.habits_limit_unit_week else R.string.habits_limit_unit_month, value, most, unit)
        else -> stringResource(if (week) R.string.habits_limit_week else R.string.habits_limit_month, value, most)
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
