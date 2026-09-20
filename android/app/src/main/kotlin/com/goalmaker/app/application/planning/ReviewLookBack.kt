package com.goalmaker.app.application.planning

import com.goalmaker.app.domain.planning.PeriodFacts
import java.time.LocalDate

/**
 * What a review of a period looks back on (docs/reviews.md): the tasks done day by day against the
 * period before, the goals and habits of the period, the tasks still open, and the facts the reactive
 * prompts read (contracts/content/prompts.json).
 */
object ReviewLookBack {
    /** A weekly review looks back over the week, a monthly one over the month, a yearly one over the year. */
    fun periodEnd(kind: String, start: LocalDate): LocalDate = when (kind) {
        ReviewRules.MONTHLY -> start.plusMonths(1).minusDays(1)
        ReviewRules.YEARLY -> start.plusYears(1).minusDays(1)
        else -> start.plusDays(6)
    }

    /** The period before the one starting on [start]. */
    fun previousStart(kind: String, start: LocalDate): LocalDate = when (kind) {
        ReviewRules.MONTHLY -> start.minusMonths(1)
        ReviewRules.YEARLY -> start.minusYears(1)
        else -> start.minusWeeks(1)
    }

    fun build(
        kind: String,
        periodStart: LocalDate,
        tasks: List<TaskItem>,
        areas: List<AreaItem>,
        goals: List<GoalItem>,
        entries: List<GoalEntryItem>,
        habits: HabitData,
        today: LocalDate,
    ): ReviewDigest {
        val end = periodEnd(kind, periodStart)
        val live = tasks.filterNot(TaskItem::deleted)
        val doneDays = live.mapNotNull { task -> task.completedDay?.let { it to task } }
        val inPeriod = doneDays.filter { (day, _) -> !day.isBefore(periodStart) && !day.isAfter(end) }
        val previous = previousStart(kind, periodStart)
        val beforeEnd = periodEnd(kind, previous)
        val doneBefore = doneDays.count { (day, _) -> !day.isBefore(previous) && !day.isAfter(beforeEnd) }

        val byDay = inPeriod.groupingBy { it.first }.eachCount()
        val days = generateSequence(periodStart) { it.plusDays(1) }
            .takeWhile { !it.isAfter(minOf(end, today)) }
            .map { day -> ReviewDigest.Day(day, byDay[day] ?: 0) }
            .toList()
        val areaNames = areas.associate { it.id to it.name }
        val strongest = inPeriod.mapNotNull { (_, task) -> task.areaId }
            .groupingBy { it }
            .eachCount()
            .maxWithOrNull(compareBy<Map.Entry<String, Int>> { it.value }.thenByDescending { it.key })
            ?.let { (id, count) -> areaNames[id]?.let { ReviewDigest.Area(it, count) } }

        // The goals of this period, and the ones of longer horizons that cover it.
        val periodGoals = goals.filter { goal ->
            goal.status != GoalRules.DROPPED &&
                !GoalRules.periodEnd(goal.horizon, goal.periodStart).isBefore(periodStart) &&
                !goal.periodStart.isAfter(end) &&
                horizonOf(kind) == goal.horizon
        }
        val entriesByGoal = entries.groupBy(GoalEntryItem::goalId)
        val tasksByGoal = live.filter { it.goalId != null }.groupBy { it.goalId!! }
        val goalRows = periodGoals.map { goal ->
            val progress = GoalRules.progressOf(goal, tasksByGoal[goal.id].orEmpty(), entriesByGoal[goal.id].orEmpty(), habits)
            ReviewDigest.Goal(goal.id, goal.title, goal.emoji, progress.fraction, progress.hit)
        }

        val habitRows = habits.habits.filterNot(HabitItem::archived).map { habit ->
            val checkins = habits.checkinsOf(habit.id)
            val pauses = habits.pausesOf(habit.id)
            val starts = HabitRules.periodsBetween(habit, periodStart, minOf(end, today))
            val states = starts.map { HabitRules.state(habit, it, today, checkins, pauses) }
            ReviewDigest.Habit(
                id = habit.id,
                name = habit.name,
                emoji = habit.emoji,
                met = states.count { it == HabitPeriodState.MET },
                periods = states.count { it != HabitPeriodState.NONE },
                streak = HabitRules.streak(habit, minOf(end, today), checkins, pauses),
            )
        }

        val open = live.filter { task ->
            task.state == TaskState.OPEN && task.plannedDate?.let { !it.isBefore(periodStart) && !it.isAfter(end) } == true
        }

        val facts = PeriodFacts(
            doneTasks = inPeriod.size,
            averageDone = doneBefore.toDouble(),
            goals = goalRows.map { goal ->
                PeriodFacts.GoalFact(goal.title, goal.fraction, expected(kind, periodStart, end, today))
            },
            habits = habitRows.map { habit ->
                PeriodFacts.HabitFact(habit.name, habit.periods - habit.met, habit.periods, habit.streak)
            },
            tasks = open.map { task -> PeriodFacts.TaskFact(task.title, moves(task)) },
        )

        return ReviewDigest(
            kind = kind,
            periodStart = periodStart,
            periodEnd = end,
            done = inPeriod.size,
            doneBefore = doneBefore,
            days = days,
            bestDay = days.filter { it.done > 0 }.maxByOrNull { it.done },
            strongestArea = strongest,
            goals = goalRows,
            habits = habitRows,
            openTasks = open,
            facts = facts,
        )
    }

    /** How much of the period has gone by, 0 to 1: what a goal should have reached by now. */
    fun expected(kind: String, start: LocalDate, end: LocalDate, today: LocalDate): Double {
        val length = (end.toEpochDay() - start.toEpochDay() + 1).toDouble()
        if (length <= 0.0) return 1.0
        val gone = (minOf(today, end).toEpochDay() - start.toEpochDay() + 1).toDouble()
        return (gone / length).coerceIn(0.0, 1.0)
    }

    /** The goal horizon a review of [kind] looks at. */
    fun horizonOf(kind: String): GoalHorizon = when (kind) {
        ReviewRules.MONTHLY -> GoalHorizon.MONTH
        ReviewRules.YEARLY -> GoalHorizon.YEAR
        else -> GoalHorizon.WEEK
    }

    // How often a task was moved: how many days it has slipped past its first plan, at most one a day.
    private fun moves(task: TaskItem): Int = task.movedCount
}
