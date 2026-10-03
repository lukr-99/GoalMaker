package com.goalmaker.app.application.planning

import com.goalmaker.app.domain.planning.PlanningDay
import com.goalmaker.app.domain.planning.RitualReminder
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * A habit's reminder (docs/reminders.md, pinned by 'habitReminders' and 'habitReminderStale' in
 * contracts/vectors/reminders.json). It rings once per planning day at the habit's [HabitItem.remindAt],
 * the way the rituals' reminders do, but only while the habit is still left that day: done, skipped,
 * failed, paused, not due, not started or a limit, it stays quiet. Quiet hours don't move it.
 */
object HabitReminder {
    /** Today's planning day when the habit's reminder came after [since] and by [now] and the habit is still left. */
    fun due(
        habit: HabitItem,
        checkins: List<HabitCheckin>,
        pauses: List<HabitPause>,
        dayStartHour: Int,
        since: LocalDateTime,
        now: LocalDateTime,
    ): LocalDate? {
        val time = habit.remindAt ?: return null
        val today = PlanningDay.of(now, dayStartHour)
        val moment = RitualReminder.momentOf(today, time, dayStartHour)
        if (!moment.isAfter(since) || moment.isAfter(now)) return null
        return today.takeIf { left(habit, it, checkins, pauses) }
    }

    /** The first moment after [now], within [DAYS_AHEAD] planning days, of a day the habit is left; null for none. */
    fun next(habit: HabitItem, checkins: List<HabitCheckin>, pauses: List<HabitPause>, dayStartHour: Int, now: LocalDateTime): LocalDateTime? {
        val time = habit.remindAt ?: return null
        var day = PlanningDay.of(now, dayStartHour)
        repeat(DAYS_AHEAD) {
            val moment = RitualReminder.momentOf(day, time, dayStartHour)
            if (moment.isAfter(now) && left(habit, day, checkins, pauses)) return moment
            day = day.plusDays(1)
        }
        return null
    }

    /** Whether a reminder on screen for [day] has to go: the planning day moved on, or the habit is no longer left. */
    fun stale(habit: HabitItem, day: LocalDate, checkins: List<HabitCheckin>, pauses: List<HabitPause>, dayStartHour: Int, now: LocalDateTime): Boolean =
        habit.deleted || habit.remindAt == null || PlanningDay.of(now, dayStartHour) != day || !left(habit, day, checkins, pauses)

    private fun left(habit: HabitItem, day: LocalDate, checkins: List<HabitCheckin>, pauses: List<HabitPause>): Boolean =
        !habit.deleted && HabitRules.standing(habit, day, checkins, pauses) == HabitStanding.LEFT

    private const val DAYS_AHEAD = 7
}
