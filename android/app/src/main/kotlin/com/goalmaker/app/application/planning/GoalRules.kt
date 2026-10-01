package com.goalmaker.app.application.planning

import java.time.LocalDate
import java.time.temporal.TemporalAdjusters
import kotlin.math.ceil

/** Goal periods, the cascade and progress (docs/goals.md, contracts/vectors/goals.json). */
object GoalRules {
    const val MODE_DONE = "done"
    const val MODE_TASKS = "tasks"
    const val MODE_NUMBER = "number"
    const val OPEN = "open"
    const val DONE = "done"
    const val DROPPED = "dropped"

    /** How far under the share of its period gone by a goal can be and still be on track. */
    const val PACE_SLACK = 0.05

    /** The share of its period gone by after which an open done-or-not goal needs you. */
    const val DUE_SOON = 0.7
    private const val EPSILON = 1e-9

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
     * Where [goal] stands, from the [tasks] that serve it, the [entries] logged on it and the
     * check-ins of the [habits] that feed a numeric goal (docs/habits.md).
     */
    fun progressOf(goal: GoalItem, tasks: List<TaskItem>, entries: List<GoalEntryItem>, habits: HabitData): GoalProgress {
        val amounts = if (goal.mode != MODE_NUMBER || habits.habits.isEmpty()) {
            emptyList()
        } else {
            HabitRules.goalAmounts(goal, habits.habits, habits.checkins).map { GoalEntryItem("", goal.id, goal.periodStart, it) }
        }
        return progress(goal.mode, goal.status, goal.target, tasks, entries + amounts)
    }

    /** The share of the [horizon] period starting on [start] gone by before [today]: 0 before it starts, 1 after it ends. */
    fun elapsed(horizon: GoalHorizon, start: LocalDate, today: LocalDate): Double {
        val length = periodEnd(horizon, start).toEpochDay() - start.toEpochDay() + 1
        val gone = (today.toEpochDay() - start.toEpochDay()).coerceIn(0, length)
        return gone.toDouble() / length
    }

    /**
     * Where [goal] stands on [today] with its [progress]: dropped, hit, behind or on track. A goal
     * counted by tasks or a number is on track while its fraction is within [PACE_SLACK] of the share of
     * its period gone by, and otherwise behind by what is missing to it, rounded up. A done-or-not goal,
     * or one with nothing to count, is behind once [DUE_SOON] of its period is gone.
     */
    fun standing(goal: GoalItem, progress: GoalProgress, today: LocalDate): GoalStanding {
        if (goal.status == DROPPED) return GoalStanding(GoalPace.DROPPED)
        if (progress.hit) return GoalStanding(GoalPace.HIT)
        val gone = elapsed(goal.horizon, goal.periodStart, today)
        if (goal.mode == MODE_DONE || progress.target <= 0.0) {
            return GoalStanding(if (gone >= DUE_SOON) GoalPace.BEHIND else GoalPace.ON_TRACK)
        }
        if (progress.fraction + PACE_SLACK >= gone) return GoalStanding(GoalPace.ON_TRACK)
        return GoalStanding(GoalPace.BEHIND, ceil(gone * progress.target - progress.value - EPSILON))
    }

    /** [items] for a rung: the ones behind first, then on track, hit and dropped, each in its own order. */
    fun <T> byPace(items: List<T>, pace: (T) -> GoalPace): List<T> = items.sortedBy { pace(it).ordinal }

    /**
     * What lights up when the goal [id] is picked: it, every goal it feeds up the cascade and every goal
     * that feeds it, however deep. Empty when [goals] has no such goal.
     */
    fun chain(goals: List<GoalItem>, id: String): Set<String> {
        val byId = goals.associateBy(GoalItem::id)
        if (id !in byId) return emptySet()
        val lit = linkedSetOf(id)
        var up = byId[id]?.parentId
        while (up != null && up in byId && lit.add(up)) up = byId[up]?.parentId
        val children = goals.filter { it.parentId != null }.groupBy { it.parentId!! }
        val down = ArrayDeque(listOf(id))
        val seen = mutableSetOf(id)
        while (down.isNotEmpty()) {
            children[down.removeFirst()].orEmpty().forEach { child ->
                if (seen.add(child.id)) {
                    lit += child.id
                    down += child.id
                }
            }
        }
        return lit
    }

    /** What the quick log adds on a numeric goal: the latest positive amount logged by hand, or null. */
    fun quickAmount(entries: List<GoalEntryItem>): Double? =
        entries.filter { !it.deleted && it.amount > 0.0 }.maxByOrNull(GoalEntryItem::createdAt)?.amount

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
