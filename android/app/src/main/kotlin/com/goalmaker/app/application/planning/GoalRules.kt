package com.goalmaker.app.application.planning

import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

/** Goal periods, the cascade and progress (docs/goals.md, contracts/vectors/goals.json). */
object GoalRules {
    const val MODE_DONE = "done"
    const val MODE_TASKS = "tasks"
    const val MODE_NUMBER = "number"
    const val OPEN = "open"
    const val DONE = "done"
    const val DROPPED = "dropped"

    /** The first day of the [horizon] period [day] falls in (weeks start on Monday). */
    fun periodStart(horizon: GoalHorizon, day: LocalDate): LocalDate = when (horizon) {
        GoalHorizon.YEAR -> day.withDayOfYear(1)
        GoalHorizon.MONTH -> day.withDayOfMonth(1)
        GoalHorizon.WEEK -> day.minusDays((day.dayOfWeek.value - 1).toLong())
        GoalHorizon.DAY -> day
    }

    /** The last day of the [horizon] period starting on [start]. */
    fun periodEnd(horizon: GoalHorizon, start: LocalDate): LocalDate = when (horizon) {
        GoalHorizon.YEAR -> start.with(TemporalAdjusters.lastDayOfYear())
        GoalHorizon.MONTH -> start.with(TemporalAdjusters.lastDayOfMonth())
        GoalHorizon.WEEK -> start.plusDays(6)
        GoalHorizon.DAY -> start
    }

    /** Whether a goal of [parent] can be the parent of a goal of [child]: a longer horizon whose period overlaps. */
    fun canServe(child: GoalHorizon, childStart: LocalDate, parent: GoalHorizon, parentStart: LocalDate): Boolean =
        parent.rank > child.rank &&
            !parentStart.isAfter(periodEnd(child, childStart)) &&
            !childStart.isAfter(periodEnd(parent, parentStart))

    /**
     * Where a goal of [mode] and [status] stands, from the [tasks] that serve it and the [entries]
     * logged on it; [target] is a numeric goal's.
     */
    fun progress(mode: String, status: String, target: Double?, tasks: List<TaskItem>, entries: List<GoalEntryItem>): GoalProgress {
        val (value, goal) = when (mode) {
            MODE_TASKS -> {
                val counted = tasks.filter { !it.deleted && it.state != TaskState.DROPPED }
                counted.count { it.state == TaskState.DONE }.toDouble() to counted.size.toDouble()
            }
            MODE_NUMBER -> entries.filterNot(GoalEntryItem::deleted).sumOf(GoalEntryItem::amount) to (target ?: 0.0)
            else -> (if (status == DONE) 1.0 else 0.0) to 1.0
        }
        val fraction = if (status == DONE) 1.0 else if (goal <= 0.0) 0.0 else (value / goal).coerceIn(0.0, 1.0)
        val hit = status == DONE || (status != DROPPED && goal > 0.0 && value >= goal)
        return GoalProgress(value, goal, fraction, hit)
    }

    /**
     * Last period's [goals] as copies for a new [horizon] period starting on [start]: all but the
     * dropped, each keeping its parent only when that goal ([parents], by id) still overlaps the period.
     */
    fun copies(goals: List<GoalItem>, parents: Map<String, GoalItem>, horizon: GoalHorizon, start: LocalDate): List<GoalCopy> =
        goals.filter { !it.deleted && it.status != DROPPED }.map { goal ->
            val parent = goal.parentId?.let(parents::get)?.takeIf { canServe(horizon, start, it.horizon, it.periodStart) }
            GoalCopy(goal.title, goal.emoji, goal.mode, goal.target, goal.unit, parent?.id)
        }
}
