package com.goalmaker.app.application.planning

import com.goalmaker.app.domain.planning.Recurrence
import java.time.LocalDate

/**
 * The week and the month view (docs/calendar.md, contracts/vectors/calendar.json): which days a grid
 * covers, and what each day holds: the tasks planned for it, the deadlines falling on it, how many
 * reminders ring, and the days a repeating task would come round to.
 */
object CalendarRules {
    const val WEEK = "week"
    const val MONTH = "month"

    // A repeat is followed at most this many times inside a range; a month grid is 42 days.
    private const val MAX_REPEATS = 60

    /** The first day of the grid: the Monday of the week, or the Monday on or before the month's first. */
    fun start(kind: String, day: LocalDate): LocalDate =
        monday(if (kind == MONTH) day.withDayOfMonth(1) else day)

    /** The last day of the grid: the Sunday closing the week, or the one closing the month's last week. */
    fun end(kind: String, day: LocalDate): LocalDate =
        monday(if (kind == MONTH) day.withDayOfMonth(day.lengthOfMonth()) else day).plusDays(6)

    /** Every day of the grid, in order, so a month is always whole weeks. */
    fun days(kind: String, day: LocalDate): List<LocalDate> {
        val last = end(kind, day)
        return generateSequence(start(kind, day)) { it.plusDays(1) }.takeWhile { !it.isAfter(last) }.toList()
    }

    /** What each day from [from] to [to] holds, in order. */
    fun build(
        tasks: List<TaskItem>,
        reminders: List<ReminderItem>,
        from: LocalDate,
        to: LocalDate,
    ): List<CalendarDay> {
        val live = tasks.filterNot(TaskItem::deleted)
        val byId = live.associateBy(TaskItem::id)
        val planned = live.filter { it.state != TaskState.DROPPED && it.plannedDate != null }
            .groupBy { it.plannedDate!! }
        val deadlines = live.filter { it.state == TaskState.OPEN && it.deadline != null }.groupBy { it.deadline!! }
        val ringing = reminders.mapNotNull { reminder ->
            byId[reminder.taskId]?.let { task -> ReminderRules.due(reminder, task)?.toLocalDate() }
        }.groupingBy { it }.eachCount()
        val repeats = repeats(live, from, to)

        return generateSequence(from) { it.plusDays(1) }.takeWhile { !it.isAfter(to) }.map { day ->
            CalendarDay(
                day = day,
                planned = order(planned[day].orEmpty()),
                deadlines = order(deadlines[day].orEmpty()),
                reminders = ringing[day] ?: 0,
                repeats = order(repeats[day].orEmpty()),
            )
        }.toList()
    }

    /** The days each repeating task would come round to inside the range, by day. */
    private fun repeats(tasks: List<TaskItem>, from: LocalDate, to: LocalDate): Map<LocalDate, List<TaskItem>> {
        // A day another occurrence of the same series is already planned for is that occurrence's, not a repeat.
        val taken = tasks.filter { it.plannedDate != null }
            .groupBy({ Occurrences.seriesOf(it) }, { it.plannedDate!! })
            .mapValues { (_, days) -> days.toSet() }
        val found = mutableMapOf<LocalDate, MutableList<TaskItem>>()
        tasks.filter { it.state == TaskState.OPEN && it.recurrence != null && it.plannedDate != null }.forEach { task ->
            val rule = Recurrence.parse(task.recurrence) ?: return@forEach
            val series = taken[Occurrences.seriesOf(task)].orEmpty()
            var day = task.plannedDate!!
            repeat(MAX_REPEATS) {
                day = rule.next(day, day) ?: return@forEach
                if (day.isAfter(to)) return@forEach
                if (!day.isBefore(from) && day !in series) found.getOrPut(day) { mutableListOf() } += task
            }
        }
        return found
    }

    // Earliest time first, tasks without a time after them, then oldest first and by id.
    private fun order(tasks: List<TaskItem>): List<TaskItem> = tasks.sortedWith(
        compareBy<TaskItem, java.time.LocalTime?>(nullsLast()) { it.plannedTime }
            .thenBy(TaskItem::createdAt)
            .thenBy(TaskItem::id),
    )

    private fun monday(day: LocalDate): LocalDate = day.minusDays((day.dayOfWeek.value - 1).toLong())
}
