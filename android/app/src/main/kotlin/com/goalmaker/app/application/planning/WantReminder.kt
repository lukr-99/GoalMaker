package com.goalmaker.app.application.planning

import com.goalmaker.app.domain.planning.PlanningDay
import com.goalmaker.app.domain.planning.RitualReminder
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/**
 * The one notification a day for wants that became ready (docs/wants.md, M8-05), pinned by the
 * 'notify' groups of contracts/vectors/wants.json. It rings at the owner's time and names the wants
 * that became ready since the last one; like the review reminders, quiet hours don't move it.
 */
object WantReminder {
    /** Mid-morning, unless the owner picks another time. */
    val DEFAULT_TIME: LocalTime = LocalTime.of(10, 0)

    /** What to show at [now]: today's notification, when its moment came after [since] and a want is newly ready. */
    fun due(time: LocalTime?, dayStartHour: Int, wants: List<WantItem>, since: LocalDateTime, now: LocalDateTime): WantsDue? {
        if (time == null) return null
        val today = PlanningDay.of(now, dayStartHour)
        val moment = RitualReminder.momentOf(today, time, dayStartHour)
        if (!moment.isAfter(since) || moment.isAfter(now)) return null
        val ready = WantRules.ready(wants, lastNotified(time, dayStartHour, since), today)
        return if (ready.isEmpty()) null else WantsDue(today, ready.map(WantItem::id))
    }

    /** The moment the alarm waits for after [now]: the time on the first day an undecided want cools. */
    fun next(time: LocalTime?, dayStartHour: Int, wants: List<WantItem>, now: LocalDateTime): LocalDateTime? {
        if (time == null) return null
        val today = PlanningDay.of(now, dayStartHour)
        return wants
            .filter { !it.deleted && it.decision == null && !it.coolsUntil.isBefore(today) }
            .map { RitualReminder.momentOf(it.coolsUntil, time, dayStartHour) }
            .filter { it.isAfter(now) }
            .minOrNull()
    }

    /** Whether a notification naming [shown] has to go: none of those wants is still waiting. */
    fun stale(shown: Collection<String>, wants: List<WantItem>): Boolean =
        wants.none { it.id in shown && !it.deleted && it.decision == null }

    // The planning day of the last notification moment at or before [since].
    private fun lastNotified(time: LocalTime, dayStartHour: Int, since: LocalDateTime): LocalDate {
        val day = PlanningDay.of(since, dayStartHour)
        return if (RitualReminder.momentOf(day, time, dayStartHour).isAfter(since)) day.minusDays(1) else day
    }
}
