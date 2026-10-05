package com.goalmaker.app.ui.calendar

import com.goalmaker.app.application.planning.AreaItem
import com.goalmaker.app.application.planning.CalendarDay
import com.goalmaker.app.application.planning.CalendarRules
import com.goalmaker.app.application.planning.EventBar
import com.goalmaker.app.application.planning.ProjectItem
import com.goalmaker.app.application.planning.TaskItem
import com.goalmaker.app.ui.habits.HabitRow
import com.goalmaker.app.ui.lists.FilterChoices
import java.time.LocalDate

/**
 * The calendar screen: the grid on show, the day the owner has opened, the [projects] its items' chips
 * name, and the area and tag [filter]. [bars] are the event bars of each week row, and [areas] every
 * area, archived ones too, for a bar's colour and the event sheet's picker.
 */
data class CalendarUiState(
    val loaded: Boolean = false,
    val kind: String = CalendarRules.MONTH,
    val anchor: LocalDate = LocalDate.MIN,
    val today: LocalDate = LocalDate.MIN,
    val days: List<CalendarDay> = emptyList(),
    val selected: LocalDate? = null,
    val projects: List<ProjectItem> = emptyList(),
    val filter: FilterChoices = FilterChoices(),
    /** The open day's habits, when the day is today or gone by: what can still be checked in there. */
    val dayHabits: List<HabitRow> = emptyList(),
    val bars: List<List<EventBar>> = emptyList(),
    val areas: List<AreaItem> = emptyList(),
) {
    /** The project a task is an item of, while that project is still there. */
    fun projectOf(task: TaskItem): ProjectItem? = task.projectId?.let { id -> projects.firstOrNull { it.id == id } }

    /** The area an event is in, while that area is still there. */
    fun areaOf(areaId: String?): AreaItem? = areaId?.let { id -> areas.firstOrNull { it.id == id } }

    /** The days as whole weeks, so a month grid draws seven to a row. */
    val weeks: List<List<CalendarDay>> get() = days.chunked(7)

    /** The day the owner opened, with what it holds. */
    val openDay: CalendarDay? get() = days.firstOrNull { it.day == selected }

    /** The busiest day on show, which the cells scale their fill against. */
    val busiest: Int get() = days.maxOfOrNull { it.count } ?: 0
}
