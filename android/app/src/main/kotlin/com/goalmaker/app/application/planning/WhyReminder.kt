package com.goalmaker.app.application.planning

import com.goalmaker.app.domain.planning.QuietHours
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/**
 * The why reminder (docs/life-goals.md, M9-04), pinned by the 'why' groups and 'fnv1a' of
 * contracts/vectors/life-goals.json. Its moment is random within each period but worked out from the
 * period, so both devices agree without stored reminder rows; quiet hours hold it back like an
 * ordinary reminder.
 */
object WhyReminder {
    private val WINDOW_START: LocalTime = LocalTime.of(10, 0)
    private const val WINDOW_MINUTES = 600L

    /** The 32-bit FNV-1a hash of [text]'s UTF-8 bytes, as an unsigned number. */
    fun fnv1a(text: String): Long {
        var hash = 0x811C9DC5L
        for (byte in text.toByteArray(Charsets.UTF_8)) {
            hash = hash xor (byte.toLong() and 0xFF)
            hash = (hash * 0x01000193L) and 0xFFFFFFFFL
        }
        return hash
    }

    /** The index of the period [day] falls in, counted from 1970-01-01 (weeks from Monday). */
    fun periodNumber(frequency: WhyFrequency, day: LocalDate): Long {
        val epochDay = day.toEpochDay()
        return when (frequency) {
            WhyFrequency.WEEKLY -> Math.floorDiv(epochDay + 3, 7L)
            else -> Math.floorDiv(epochDay, frequency.days.toLong())
        }
    }

    /** The first day of period [number]. */
    fun periodStart(frequency: WhyFrequency, number: Long): LocalDate = when (frequency) {
        WhyFrequency.WEEKLY -> LocalDate.ofEpochDay(number * 7 - 3)
        else -> LocalDate.ofEpochDay(number * frequency.days)
    }

    /** The moment of the period [day] falls in. Not for [WhyFrequency.OFF]. */
    fun moment(frequency: WhyFrequency, day: LocalDate, quietHours: QuietHours): WhyMoment =
        momentOf(frequency, periodNumber(frequency, day), quietHours)

    /** The open life goal the period starting [periodStart] shows; null with none open. */
    fun goal(frequency: WhyFrequency, periodStart: LocalDate, goals: List<LifeGoalItem>): LifeGoalItem? {
        val open = LifeGoalRules.open(goals)
        if (open.isEmpty()) return null
        return open[Math.floorMod(periodNumber(frequency, periodStart), open.size.toLong()).toInt()]
    }

    /** What a look at [now] shows: the latest moment after [since] and up to [now], if any. */
    fun due(frequency: WhyFrequency, quietHours: QuietHours, since: LocalDateTime, now: LocalDateTime): WhyMoment? {
        if (frequency == WhyFrequency.OFF) return null
        val current = periodNumber(frequency, now.toLocalDate())
        return (current - 2..current)
            .map { momentOf(frequency, it, quietHours) }
            .lastOrNull { it.at.isAfter(since) && !it.at.isAfter(now) }
    }

    /** The first moment after [now], for the alarm; null when off. */
    fun next(frequency: WhyFrequency, quietHours: QuietHours, now: LocalDateTime): LocalDateTime? {
        if (frequency == WhyFrequency.OFF) return null
        val current = periodNumber(frequency, now.toLocalDate())
        return (current - 1..current + 2)
            .map { momentOf(frequency, it, quietHours).at }
            .filter { it.isAfter(now) }
            .minOrNull()
    }

    private fun momentOf(frequency: WhyFrequency, number: Long, quietHours: QuietHours): WhyMoment {
        val start = periodStart(frequency, number)
        val seed = fnv1a("${frequency.key}:$start")
        val length = frequency.days.toLong()
        val at = start.plusDays(seed % length)
            .atTime(WINDOW_START)
            .plusMinutes((seed / length) % WINDOW_MINUTES)
        return WhyMoment(start, quietHours.release(at, important = false))
    }
}
