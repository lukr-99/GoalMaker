package com.goalmaker.app.ui.calendar

import com.goalmaker.app.application.planning.CalendarDay
import com.goalmaker.app.application.planning.CalendarRules
import java.time.LocalDate

/** The calendar screen: the grid on show, and the day the owner has opened. */
data class CalendarUiState(
    val loaded: Boolean = false,
    val kind: String = CalendarRules.MONTH,
    val anchor: LocalDate = LocalDate.MIN,
    val today: LocalDate = LocalDate.MIN,
    val days: List<CalendarDay> = emptyList(),
    val selected: LocalDate? = null,
) {
    /** The days as whole weeks, so a month grid draws seven to a row. */
    val weeks: List<List<CalendarDay>> get() = days.chunked(7)

    /** The day the owner opened, with what it holds. */
    val openDay: CalendarDay? get() = days.firstOrNull { it.day == selected }

    /** The busiest day on show, which the cells scale their fill against. */
    val busiest: Int get() = days.maxOfOrNull { it.count } ?: 0
}
