package com.goalmaker.app.data.planning

import android.content.Intent
import androidx.annotation.StringRes
import com.goalmaker.app.R
import com.goalmaker.app.application.planning.HabitItem
import com.goalmaker.app.application.planning.HabitRules
import com.goalmaker.app.application.planning.ReminderService
import java.time.LocalDate

/**
 * The buttons on a habit's reminder (docs/reminders.md), and what the receiver does with them. A check
 * habit gets Check in, a count +1 and an amount Log, which opens the app on its log dialog because it
 * asks for a number; an amount that is not a limit and has some of its target left gets Fill before it,
 * which logs the rest in one tap (docs/habits.md, "One tap"). Every habit gets the skip of its period.
 * The same words as the Windows toast.
 */
object HabitReminderButtons {
    /** One button: its label, and the broadcast it sends (Log opens the app instead). */
    data class Button(@param:StringRes val label: Int, val action: String)

    /** The buttons [habit]'s reminder shows, with [value] logged on its day so far. */
    fun of(habit: HabitItem, value: Double = 0.0): List<Button> = listOfNotNull(
        Button(R.string.habit_reminder_fill, ReminderAlarm.ACTION_HABIT_FILL).takeIf { HabitRules.fill(habit, value) != null },
        when (habit.measure) {
            HabitRules.COUNT -> Button(R.string.habit_reminder_add_one, ReminderAlarm.ACTION_HABIT_CHECK_IN)
            HabitRules.AMOUNT -> Button(R.string.habit_reminder_log, ReminderAlarm.ACTION_HABIT_LOG)
            else -> Button(R.string.habit_reminder_check_in, ReminderAlarm.ACTION_HABIT_CHECK_IN)
        },
        Button(
            when (habit.cadence) {
                HabitRules.PER_WEEK -> R.string.habits_skip_week
                HabitRules.PER_MONTH -> R.string.habits_skip_month
                else -> R.string.habits_skip_day
            },
            ReminderAlarm.ACTION_HABIT_SKIP,
        ),
    )

    /**
     * Settles a habit reminder's button: checks in or skips through [reminders], then takes the
     * notification down. False when [intent] isn't a habit button or doesn't say which habit and day.
     */
    fun settle(intent: Intent, reminders: ReminderService, notifications: ReminderNotifications): Boolean {
        val habitId = intent.getStringExtra(ReminderAlarm.EXTRA_HABIT_ID) ?: return false
        val day = intent.getStringExtra(ReminderAlarm.EXTRA_HABIT_DAY)
            ?.let { runCatching { LocalDate.parse(it) }.getOrNull() } ?: return false
        when (intent.action) {
            ReminderAlarm.ACTION_HABIT_CHECK_IN -> reminders.checkInHabit(habitId, day)
            ReminderAlarm.ACTION_HABIT_FILL -> reminders.fillHabit(habitId, day)
            ReminderAlarm.ACTION_HABIT_SKIP -> reminders.skipHabit(habitId, day)
            else -> return false
        }
        notifications.clearHabit(habitId, day)
        return true
    }
}
