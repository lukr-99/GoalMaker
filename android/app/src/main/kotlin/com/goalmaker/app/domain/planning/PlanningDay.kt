package com.goalmaker.app.domain.planning

import java.time.LocalDate
import java.time.LocalDateTime

/**
 * The day plans belong to: the local date shifted back by the hour the day starts (docs/lists.md),
 * so planning at 01:30 still happens on yesterday's day.
 */
object PlanningDay {
    const val DEFAULT_START_HOUR = 4
    val START_HOURS = 0..6

    fun of(now: LocalDateTime, startHour: Int = DEFAULT_START_HOUR): LocalDate = now.minusHours(startHour.toLong()).toLocalDate()
}
