package com.goalmaker.app.domain.planning

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/**
 * The evening Plan tomorrow reminder (docs/reminders.md, pinned by the 'ritual' cases in
 * contracts/vectors/reminders.json). It rings once per planning day at the time the owner chose,
 * unless the ritual was already done or skipped that day on either device. Quiet hours don't move it:
 * the owner picked the time.
 */
object RitualReminder {
    /** 20:00, until the owner picks another time or switches it off. */
    val DEFAULT_TIME: LocalTime = LocalTime.of(20, 0)

    /** The first moment of planning day [day] whose clock reads [time]: after midnight when [time] comes before the day's start. */
    fun momentOf(day: LocalDate, time: LocalTime, dayStartHour: Int): LocalDateTime =
        if (time.hour >= dayStartHour) day.atTime(time) else day.plusDays(1).atTime(time)

    /** The planning day whose reminder to show at [now]: today's, when its moment came after [since]. Never an earlier day. */
    fun due(time: LocalTime?, dayStartHour: Int, ran: Set<LocalDate>, since: LocalDateTime, now: LocalDateTime): LocalDate? {
        if (time == null) return null
        val today = PlanningDay.of(now, dayStartHour)
        val moment = momentOf(today, time, dayStartHour)
        return today.takeIf { it !in ran && moment.isAfter(since) && !moment.isAfter(now) }
    }

    /** The moment the alarm waits for after [now], or null when the reminder is off. */
    fun next(time: LocalTime?, dayStartHour: Int, ran: Set<LocalDate>, now: LocalDateTime): LocalDateTime? {
        if (time == null) return null
        var day = PlanningDay.of(now, dayStartHour)
        repeat(DAYS_AHEAD) {
            val moment = momentOf(day, time, dayStartHour)
            if (day !in ran && moment.isAfter(now)) return moment
            day = day.plusDays(1)
        }
        return null
    }

    /** Whether a reminder on screen for [day] has to go: the ritual ran, or the planning day moved on. */
    fun stale(day: LocalDate, dayStartHour: Int, ran: Set<LocalDate>, now: LocalDateTime): Boolean =
        day in ran || PlanningDay.of(now, dayStartHour) != day

    private const val DAYS_AHEAD = 3
}
