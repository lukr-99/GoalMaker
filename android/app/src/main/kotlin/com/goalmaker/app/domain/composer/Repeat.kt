package com.goalmaker.app.domain.composer

import java.time.DayOfWeek
import java.time.LocalDate

/**
 * A parsed repeat before it's pinned to a planned date. [days] or [monthDay] are null when the
 * pattern takes them from the planned date (`weekly`, `every 2 weeks`, `monthly`).
 */
internal data class Repeat(
    val frequency: Frequency,
    val interval: Int = 1,
    val days: Set<DayOfWeek>? = null,
    val monthDay: Int? = null,
) {
    enum class Frequency { DAILY, WEEKLY, MONTHLY }

    /** The first day on or after [earliest] this repeat falls on. */
    fun firstOccurrence(earliest: LocalDate): LocalDate = when {
        frequency == Frequency.WEEKLY && days != null ->
            generateSequence(earliest) { it.plusDays(1) }.first { it.dayOfWeek in days }
        frequency == Frequency.MONTHLY && monthDay != null ->
            generateSequence(earliest.withDayOfMonth(1)) { it.plusMonths(1) }
                .mapNotNull { month -> month.takeIf { monthDay <= it.lengthOfMonth() }?.withDayOfMonth(monthDay) }
                .first { !it.isBefore(earliest) }
        else -> earliest
    }

    /** The RRULE subset, with any missing weekday or day of month taken from [planned]. */
    fun rule(planned: LocalDate): String = buildString {
        append("FREQ=").append(frequency.name)
        if (interval > 1) append(";INTERVAL=").append(interval)
        when (frequency) {
            Frequency.WEEKLY -> append(";BYDAY=").append(
                (days ?: setOf(planned.dayOfWeek)).sorted().joinToString(",") { it.name.take(2) },
            )
            Frequency.MONTHLY -> append(";BYMONTHDAY=").append(monthDay ?: planned.dayOfMonth)
            Frequency.DAILY -> Unit
        }
    }
}
