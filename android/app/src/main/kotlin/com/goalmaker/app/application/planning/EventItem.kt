package com.goalmaker.app.application.planning

import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * A calendar event (docs/calendar.md, ADR 0019): something that takes up days rather than gets done,
 * from [startsOn] to [endsOn], both days included. It is never ticked off.
 */
data class EventItem(
    val id: String,
    val title: String,
    val startsOn: LocalDate,
    val endsOn: LocalDate,
    val notes: String? = null,
    val areaId: String? = null,
    val madeBy: String = ProjectRules.OWNER,
    val createdAt: String = "",
    val deleted: Boolean = false,
) {
    /** How many days the event has, 1 for a one-day event. */
    val days: Int get() = ChronoUnit.DAYS.between(startsOn, endsOn).toInt() + 1

    /** Whether [day] is one of the event's days. */
    fun covers(day: LocalDate): Boolean = !day.isBefore(startsOn) && !day.isAfter(endsOn)
}
