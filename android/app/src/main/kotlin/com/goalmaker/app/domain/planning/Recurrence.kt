package com.goalmaker.app.domain.planning

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.util.Locale

/**
 * A repeat rule the apps can follow (docs/repeating.md): the RRULE subset the composer writes.
 * [days] and [monthDay] are null when the rule takes them from the anchor.
 */
data class Recurrence(
    val frequency: Frequency,
    val interval: Int,
    val days: Set<DayOfWeek>?,
    val monthDay: Int?,
) {
    enum class Frequency { DAILY, WEEKLY, MONTHLY }

    /**
     * The next occurrence's day: the first match after the later of [planned] and [today], counting
     * from [planned] (today when there is none).
     */
    fun next(planned: LocalDate?, today: LocalDate): LocalDate? {
        val anchor = planned ?: today
        var day = maxOf(anchor, today)
        repeat(SEARCH_DAYS) {
            day = day.plusDays(1)
            if (matches(day, anchor)) return day
        }
        return null
    }

    private fun matches(day: LocalDate, anchor: LocalDate): Boolean = when (frequency) {
        Frequency.DAILY -> ChronoUnit.DAYS.between(anchor, day) % interval == 0L
        Frequency.WEEKLY -> ChronoUnit.WEEKS.between(monday(anchor), monday(day)) % interval == 0L &&
            day.dayOfWeek in (days ?: setOf(anchor.dayOfWeek))
        Frequency.MONTHLY -> ((day.year - anchor.year) * 12 + day.monthValue - anchor.monthValue) % interval == 0 &&
            day.dayOfMonth == (monthDay ?: anchor.dayOfMonth)
    }

    companion object {
        // Ten years of days: far past any rule's next match; a rule that finds nothing in it has none.
        private const val SEARCH_DAYS = 3700
        private val PARTS = setOf("FREQ", "INTERVAL", "BYDAY", "BYMONTHDAY")
        private val WEEKDAYS = DayOfWeek.entries.associateBy { it.name.take(2) }

        /** The rule in [text], or null when it isn't one the apps can follow. */
        fun parse(text: String?): Recurrence? {
            if (text.isNullOrBlank()) return null
            val parts = mutableMapOf<String, String>()
            for (part in text.trim().uppercase(Locale.ROOT).split(';')) {
                val pair = part.split('=', limit = 2)
                if (pair.size != 2 || pair[0] !in PARTS || parts.put(pair[0], pair[1]) != null) return null
            }
            val frequency = when (parts["FREQ"]) {
                "DAILY" -> Frequency.DAILY
                "WEEKLY" -> Frequency.WEEKLY
                "MONTHLY" -> Frequency.MONTHLY
                else -> return null
            }
            val interval = parts["INTERVAL"]?.let { text -> text.takeIf { it.all(Char::isDigit) }?.toIntOrNull() ?: return null } ?: 1
            if (interval < 1) return null
            val days = parts["BYDAY"]?.let { text ->
                if (frequency != Frequency.WEEKLY) return null
                text.split(',').map { WEEKDAYS[it] ?: return null }.toSet()
            }
            val monthDay = parts["BYMONTHDAY"]?.let { text ->
                if (frequency != Frequency.MONTHLY) return null
                text.takeIf { it.all(Char::isDigit) }?.toIntOrNull()?.takeIf { it in 1..31 } ?: return null
            }
            return Recurrence(frequency, interval, days, monthDay)
        }

        private fun monday(day: LocalDate): LocalDate = day.minusDays((day.dayOfWeek.value - 1).toLong())
    }
}
