package com.goalmaker.app.domain.planning

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/**
 * The reminders that call the owner to a review (docs/reviews.md, spec story 55, pinned by the
 * 'reviewReminders' cases in contracts/vectors/reminders.json). The weekly one rings on the weekday
 * the owner chose, the monthly one on the first day of a month, each once per planning day and only
 * while the review hasn't been written. Quiet hours don't move them: the owner picked the time.
 */
object ReviewReminder {
    /** Sunday evening for the week, the first evening of a month for the month. */
    val DEFAULT_TIME: LocalTime = LocalTime.of(18, 0)
    val DEFAULT_WEEKDAY: Int = DayOfWeek.SUNDAY.value

    /** Whether a reminder of [kind] falls on [day]: the chosen weekday, or the first of a month. */
    fun isReminderDay(kind: String, day: LocalDate, weekday: Int): Boolean =
        if (kind == MONTHLY) day.dayOfMonth == 1 else day.dayOfWeek.value == weekday

    /**
     * The period a reminder on [day] looks back on: the week ending that weekend, the week just gone
     * earlier in the week, or the month just gone.
     */
    fun periodStart(kind: String, day: LocalDate): LocalDate = if (kind == MONTHLY) {
        day.withDayOfMonth(1).minusMonths(1)
    } else {
        val monday = day.minusDays((day.dayOfWeek.value - 1).toLong())
        if (day.dayOfWeek.value >= DayOfWeek.SATURDAY.value) monday else monday.minusWeeks(1)
    }

    /** The planning day whose reminder to show at [now]: today's, when its moment came after [since]. */
    fun due(
        kind: String,
        time: LocalTime?,
        weekday: Int,
        dayStartHour: Int,
        ran: Set<LocalDate>,
        since: LocalDateTime,
        now: LocalDateTime,
    ): LocalDate? {
        if (time == null) return null
        val today = PlanningDay.of(now, dayStartHour)
        if (!isReminderDay(kind, today, weekday) || today in ran) return null
        val moment = RitualReminder.momentOf(today, time, dayStartHour)
        return today.takeIf { moment.isAfter(since) && !moment.isAfter(now) }
    }

    /** The moment the alarm waits for after [now], or null when the reminder is off. */
    fun next(kind: String, time: LocalTime?, weekday: Int, dayStartHour: Int, ran: Set<LocalDate>, now: LocalDateTime): LocalDateTime? {
        if (time == null) return null
        var day = PlanningDay.of(now, dayStartHour)
        repeat(DAYS_AHEAD) {
            val moment = RitualReminder.momentOf(day, time, dayStartHour)
            if (isReminderDay(kind, day, weekday) && day !in ran && moment.isAfter(now)) return moment
            day = day.plusDays(1)
        }
        return null
    }

    /** Whether a reminder on screen for [day] has to go: the review ran, or the planning day moved on. */
    fun stale(day: LocalDate, dayStartHour: Int, ran: Set<LocalDate>, now: LocalDateTime): Boolean =
        day in ran || PlanningDay.of(now, dayStartHour) != day

    private const val MONTHLY = "monthly"

    // A monthly reminder can be up to a month away.
    private const val DAYS_AHEAD = 40
}
