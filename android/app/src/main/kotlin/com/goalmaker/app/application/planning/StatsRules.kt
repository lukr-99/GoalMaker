package com.goalmaker.app.application.planning

import java.time.LocalDate

/**
 * The numbers behind the stats screen (docs/stats.md, contracts/vectors/stats.json): tasks finished a
 * week at a time, goals hit a month at a time, how each habit is holding up, and past ratings.
 */
object StatsRules {
    /** How many weeks, months and reviews the screen shows by default. */
    const val WEEKS = 12
    const val MONTHS = 6
    const val RATINGS = 12

    fun build(
        tasks: List<TaskItem>,
        goals: List<GoalItem>,
        entries: List<GoalEntryItem>,
        habits: HabitData,
        reviews: List<ReviewItem>,
        today: LocalDate,
        weekCount: Int = WEEKS,
        monthCount: Int = MONTHS,
        ratingKind: String = ReviewRules.WEEKLY,
        ratingCount: Int = RATINGS,
    ): StatsDigest = StatsDigest(
        weeks = weeks(tasks, today, weekCount),
        months = months(goals, tasks, entries, habits, today, monthCount),
        habits = habits(habits, today, weekCount),
        ratings = ratings(reviews, ratingKind, ratingCount),
    )

    /** Tasks finished in each of the last [count] weeks, oldest first, the last one holding [today]. */
    fun weeks(tasks: List<TaskItem>, today: LocalDate, count: Int = WEEKS): List<StatsDigest.Week> {
        val last = GoalRules.periodStart(GoalHorizon.WEEK, today)
        val first = last.minusWeeks((count - 1).coerceAtLeast(0).toLong())
        val byWeek = tasks.filterNot(TaskItem::deleted)
            .mapNotNull(TaskItem::completedDay)
            .filter { !it.isBefore(first) && !it.isAfter(today) }
            .groupingBy { GoalRules.periodStart(GoalHorizon.WEEK, it) }
            .eachCount()
        return (0 until count.coerceAtLeast(0)).map { step ->
            val start = first.plusWeeks(step.toLong())
            StatsDigest.Week(start, byWeek[start] ?: 0)
        }
    }

    /** The goals of each of the last [count] months, oldest first, and how many of them were hit. */
    fun months(
        goals: List<GoalItem>,
        tasks: List<TaskItem>,
        entries: List<GoalEntryItem>,
        habits: HabitData,
        today: LocalDate,
        count: Int = MONTHS,
    ): List<StatsDigest.Month> {
        val last = today.withDayOfMonth(1)
        val first = last.minusMonths((count - 1).coerceAtLeast(0).toLong())
        val live = goals.filter { !it.deleted && it.status != GoalRules.DROPPED && it.horizon == GoalHorizon.MONTH }
        val tasksByGoal = tasks.filter { !it.deleted && it.goalId != null }.groupBy { it.goalId!! }
        val entriesByGoal = entries.groupBy(GoalEntryItem::goalId)
        return (0 until count.coerceAtLeast(0)).map { step ->
            val start = first.plusMonths(step.toLong())
            val month = live.filter { GoalRules.periodStart(GoalHorizon.MONTH, it.periodStart) == start }
            val hit = month.count { goal ->
                GoalRules.progressOf(goal, tasksByGoal[goal.id].orEmpty(), entriesByGoal[goal.id].orEmpty(), habits).hit
            }
            StatsDigest.Month(start, hit, month.size)
        }
    }

    /** How each habit that is not archived did over the last [weeks] weeks, in the order they are kept. */
    fun habits(habits: HabitData, today: LocalDate, weeks: Int = WEEKS): List<StatsDigest.Habit> {
        val from = GoalRules.periodStart(GoalHorizon.WEEK, today).minusWeeks((weeks - 1).coerceAtLeast(0).toLong())
        return habits.habits.filter { !it.deleted && !it.archived }.map { habit ->
            val checkins = habits.checkinsOf(habit.id)
            val pauses = habits.pausesOf(habit.id)
            val states = HabitRules.periodsBetween(habit, from, today).map { HabitRules.state(habit, it, today, checkins, pauses) }
            StatsDigest.Habit(
                id = habit.id,
                name = habit.name,
                emoji = habit.emoji,
                met = states.count { it == HabitPeriodState.MET },
                periods = states.count { it != HabitPeriodState.NONE },
                streak = HabitRules.streak(habit, today, checkins, pauses),
                best = best(states),
            )
        }
    }

    /** The ratings of the last [count] reviews of [kind] that rated anything, oldest first. */
    fun ratings(reviews: List<ReviewItem>, kind: String = ReviewRules.WEEKLY, count: Int = RATINGS): List<StatsDigest.Rating> =
        reviews.filter { !it.deleted && it.kind == kind && (it.mood != null || it.energy != null) }
            .sortedBy(ReviewItem::periodStart)
            .takeLast(count.coerceAtLeast(0))
            .map { StatsDigest.Rating(it.periodStart, it.mood, it.energy) }

    // The longest run of met periods: a missed one ends a run, the rest are passed over like a streak.
    private fun best(states: List<HabitPeriodState>): Int {
        var best = 0
        var run = 0
        states.forEach { state ->
            when (state) {
                HabitPeriodState.MET -> {
                    run++
                    if (run > best) best = run
                }
                HabitPeriodState.MISSED -> run = 0
                else -> Unit
            }
        }
        return best
    }
}
