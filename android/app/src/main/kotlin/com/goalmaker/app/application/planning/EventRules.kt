package com.goalmaker.app.application.planning

import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * Calendar events (docs/calendar.md, ADR 0019), pinned by the 'eventDays', 'bars', 'ongoing' and
 * 'picked' groups of contracts/vectors/calendar.json, which the Windows app and the connector run too:
 * which events a day holds, the bars a grid's week rows draw, the events going on today, and what the
 * calendar's bottom bar makes from the days picked.
 */
object EventRules {
    const val MAX_TITLE = 200
    const val MAX_NOTES = 10_000

    /** The most days the last day may be after the first. */
    const val MAX_SPAN = 366L

    /** A day's order: earliest first day, then the longest, then by title, then by id. */
    val ORDER: Comparator<EventItem> = compareBy<EventItem> { it.startsOn }
        .thenByDescending { it.endsOn }
        .thenBy { it.title }
        .thenBy { it.id }

    /** Whether the first and last day make an event the server takes. */
    fun validSpan(startsOn: LocalDate, endsOn: LocalDate): Boolean =
        !endsOn.isBefore(startsOn) && ChronoUnit.DAYS.between(startsOn, endsOn) <= MAX_SPAN

    /** The events [day] holds, in the day's order; only the ones [keep] says yes to show. */
    fun onDay(events: List<EventItem>, day: LocalDate, keep: (EventItem) -> Boolean = { true }): List<EventItem> =
        events.filter { !it.deleted && it.covers(day) && keep(it) }.sortedWith(ORDER)

    /** The events of each day from [from] to [to], by day. */
    fun days(
        events: List<EventItem>,
        from: LocalDate,
        to: LocalDate,
        keep: (EventItem) -> Boolean = { true },
    ): Map<LocalDate, List<EventItem>> {
        val shown = touching(events, from, to, keep)
        return generateSequence(from) { it.plusDays(1) }.takeWhile { !it.isAfter(to) }
            .associateWith { day -> shown.filter { it.covers(day) } }
    }

    /**
     * The bars of a grid from [start] (a Monday) to [end] (a Sunday), a list per week row, each by lane.
     * Lanes are given over the whole grid in the day order, each event taking the lowest lane whose
     * last event ended before its first day in the grid, so an event keeps its lane from row to row.
     */
    fun bars(
        events: List<EventItem>,
        start: LocalDate,
        end: LocalDate,
        keep: (EventItem) -> Boolean = { true },
    ): List<List<EventBar>> {
        val shown = touching(events, start, end, keep)
        val laneEnds = mutableListOf<LocalDate>()
        val lanes = shown.associate { event ->
            val first = maxOf(event.startsOn, start)
            var lane = laneEnds.indexOfFirst { it.isBefore(first) }
            if (lane < 0) {
                lane = laneEnds.size
                laneEnds += event.endsOn
            } else {
                laneEnds[lane] = event.endsOn
            }
            event.id to lane
        }
        val rows = (ChronoUnit.DAYS.between(start, end).toInt() + 1 + 6) / 7
        return (0 until rows).map { row ->
            val rowStart = start.plusDays(row * 7L)
            val rowEnd = rowStart.plusDays(6)
            shown.filter { !it.startsOn.isAfter(rowEnd) && !it.endsOn.isBefore(rowStart) }.map { event ->
                EventBar(
                    event = event,
                    from = ChronoUnit.DAYS.between(rowStart, maxOf(event.startsOn, rowStart)).toInt(),
                    to = ChronoUnit.DAYS.between(rowStart, minOf(event.endsOn, rowEnd)).toInt(),
                    lane = lanes.getValue(event.id),
                    before = event.startsOn.isBefore(rowStart),
                    after = event.endsOn.isAfter(rowEnd),
                )
            }.sortedWith(compareBy<EventBar> { it.lane }.thenBy { it.from })
        }
    }

    /** The events [day] falls inside, in the day's order, with which of its days it is. */
    fun ongoing(events: List<EventItem>, day: LocalDate): List<OngoingEvent> = onDay(events, day).map { event ->
        OngoingEvent(event, ChronoUnit.DAYS.between(event.startsOn, day).toInt() + 1, event.days)
    }

    /**
     * The first and last day of the one event a pick of days makes: the earliest picked day to the
     * latest, gaps included. Null when nothing is picked or the pick is wider than an event may be.
     */
    fun pickedSpan(picked: Collection<LocalDate>): ClosedRange<LocalDate>? {
        val first = picked.minOrNull() ?: return null
        val last = picked.max()
        return (first..last).takeIf { validSpan(first, last) }
    }

    /** The days a pick puts a copy of a task on: each picked day once, earliest first. */
    fun pickedDays(picked: Collection<LocalDate>): List<LocalDate> = picked.distinct().sorted()

    private fun touching(events: List<EventItem>, from: LocalDate, to: LocalDate, keep: (EventItem) -> Boolean) =
        events.filter { !it.deleted && !it.startsOn.isAfter(to) && !it.endsOn.isBefore(from) && keep(it) }.sortedWith(ORDER)
}
