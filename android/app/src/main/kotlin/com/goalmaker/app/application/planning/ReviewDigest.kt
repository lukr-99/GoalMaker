package com.goalmaker.app.application.planning

import com.goalmaker.app.domain.planning.PeriodFacts
import java.time.LocalDate

/**
 * The look back a review opens with (docs/reviews.md): how much was done against the period before,
 * the day and the area that carried it, the goals and habits of the period, the tasks still open, and
 * the [facts] the reactive prompts read.
 */
data class ReviewDigest(
    val kind: String = ReviewRules.WEEKLY,
    val periodStart: LocalDate = LocalDate.MIN,
    val periodEnd: LocalDate = LocalDate.MIN,
    val done: Int = 0,
    val doneBefore: Int = 0,
    val days: List<Day> = emptyList(),
    val bestDay: Day? = null,
    val strongestArea: Area? = null,
    val goals: List<Goal> = emptyList(),
    val habits: List<Habit> = emptyList(),
    val openTasks: List<TaskItem> = emptyList(),
    val facts: PeriodFacts = PeriodFacts(),
) {
    /** How the period compares with the one before: the difference in tasks done. */
    val change: Int get() = done - doneBefore

    data class Day(val day: LocalDate, val done: Int)

    data class Area(val name: String, val done: Int)

    data class Goal(val id: String, val title: String, val emoji: String?, val fraction: Double, val hit: Boolean)

    data class Habit(val id: String, val name: String, val emoji: String?, val met: Int, val periods: Int, val streak: Int)
}
