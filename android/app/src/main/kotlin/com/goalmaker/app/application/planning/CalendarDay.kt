package com.goalmaker.app.application.planning

import java.time.LocalDate

/**
 * One day of the calendar (docs/calendar.md): what is planned for it, what is due on it, how many
 * reminders ring, the repeating tasks that would come round to it, and the [events] it falls inside.
 */
data class CalendarDay(
    val day: LocalDate,
    val planned: List<TaskItem> = emptyList(),
    val deadlines: List<TaskItem> = emptyList(),
    val reminders: Int = 0,
    val repeats: List<TaskItem> = emptyList(),
    val events: List<EventItem> = emptyList(),
) {
    /** Whether the day has nothing on it at all. */
    val empty: Boolean get() = planned.isEmpty() && deadlines.isEmpty() && reminders == 0 && repeats.isEmpty() && events.isEmpty()

    /** How many tasks the day holds, for the bar a cell shows under its number; events draw bars of their own. */
    val count: Int get() = planned.size + deadlines.size + repeats.size

    /** How many of the day's planned tasks are done, so a cell can show what is left. */
    val done: Int get() = planned.count { it.state == TaskState.DONE }
}
