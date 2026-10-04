package com.goalmaker.app.application.planning

import java.time.LocalDate

/**
 * What the stats screen shows (docs/stats.md, spec stories 64 and 67): tasks finished week by week,
 * goals hit month by month, how each habit is holding up, and the mood and energy of past reviews.
 */
data class StatsDigest(
    val weeks: List<Week> = emptyList(),
    val months: List<Month> = emptyList(),
    val habits: List<Habit> = emptyList(),
    val ratings: List<Rating> = emptyList(),
    /** The project work over the weeks on show, per project, most first. */
    val byProject: List<ProjectDone> = emptyList(),
) {
    /** Tasks finished over the weeks on show. */
    val done: Int get() = weeks.sumOf(Week::done)

    /** How many of them were project work: items of a project that is still there. */
    val projectWork: Int get() = weeks.sumOf(Week::project)

    /** And how many were everything else. */
    val otherWork: Int get() = done - projectWork

    /** What that works out at a week. */
    val perWeek: Double get() = if (weeks.isEmpty()) 0.0 else done.toDouble() / weeks.size

    /** The fullest week, the earliest of them when two tie, or null when nothing was finished. */
    val bestWeek: Week? get() = weeks.filter { it.done > 0 }.maxByOrNull { it.done }

    val goalsHit: Int get() = months.sumOf(Month::hit)

    val goalsTotal: Int get() = months.sumOf(Month::total)

    /** Habit periods met against the ones that asked for something, 0 to 1. */
    val habitRate: Double get() {
        val periods = habits.sumOf(Habit::periods)
        return if (periods == 0) 0.0 else habits.sumOf(Habit::met).toDouble() / periods
    }

    /** Whether there is nothing to show yet, so the screen can say so instead of drawing empty charts. */
    val empty: Boolean get() = done == 0 && goalsTotal == 0 && habits.isEmpty() && ratings.isEmpty()

    /**
     * One column of the tasks chart: the Monday it starts on, what was finished that week and how
     * much of it was [project] work.
     */
    data class Week(val start: LocalDate, val done: Int, val project: Int = 0) {
        val other: Int get() = done - project
    }

    /** One project's finished items over the weeks on show. */
    data class ProjectDone(val id: String, val name: String, val done: Int)

    /** One column of the goals chart: the first of the month, the goals it held and how many were hit. */
    data class Month(val start: LocalDate, val hit: Int, val total: Int) {
        val fraction: Double get() = if (total == 0) 0.0 else hit.toDouble() / total
    }

    /** A habit over the window: periods met, the run going now and the longest run inside the window. */
    data class Habit(
        val id: String,
        val name: String,
        val emoji: String?,
        val met: Int,
        val periods: Int,
        val streak: Int,
        val best: Int,
    ) {
        val rate: Double get() = if (periods == 0) 0.0 else met.toDouble() / periods
    }

    /** One review's ratings, for the mood and energy chart (spec story 64). */
    data class Rating(val periodStart: LocalDate, val mood: Int?, val energy: Int?)
}
