package com.goalmaker.app.application.planning

import java.time.LocalDate

/**
 * The numbers behind the stats screen (docs/stats.md, contracts/vectors/stats.json): tasks finished a
 * week at a time with the project work counted apart, goals hit a month at a time, how each habit is
 * holding up, and past ratings.
 */
object StatsRules {
    /** How many weeks, months and reviews the screen shows by default. */
    const val WEEKS = 12
    const val MONTHS = 6
    const val RATINGS = 12

    /** How many projects the stats screen's By project block lists. */
    const val TOP_PROJECTS = 5

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
        projects: List<ProjectItem> = emptyList(),
    ): StatsDigest = StatsDigest(
        weeks = weeks(tasks, today, weekCount, projects),
        months = months(goals, tasks, entries, habits, today, monthCount),
        habits = habits(habits, today, weekCount),
        ratings = ratings(reviews, ratingKind, ratingCount),
        byProject = byProject(tasks, projects, today, weekCount),
    )

    /**
     * Tasks finished in each of the last [count] weeks, oldest first, the last one holding [today],
     * and how many of them were project work: items of one of [projects] that is not deleted.
     */
    fun weeks(
        tasks: List<TaskItem>,
        today: LocalDate,
        count: Int = WEEKS,
        projects: List<ProjectItem> = emptyList(),
    ): List<StatsDigest.Week> {
        val first = firstWeek(today, count)
        val live = liveProjects(projects)
        val byWeek = finished(tasks, first, today).groupBy(
            { (day, _) -> GoalRules.periodStart(GoalHorizon.WEEK, day) },
            { (_, task) -> task.projectId?.let(live::contains) == true },
        )
        return (0 until count.coerceAtLeast(0)).map { step ->
            val start = first.plusWeeks(step.toLong())
            val week = byWeek[start].orEmpty()
            StatsDigest.Week(start, week.size, week.count { it })
        }
    }

    /**
     * The project work of the last [count] weeks per project that is not deleted, most first; the
     * projects with none are left out, and ties keep the order [projects] has them in.
     */
    fun byProject(tasks: List<TaskItem>, projects: List<ProjectItem>, today: LocalDate, count: Int = WEEKS): List<StatsDigest.ProjectDone> {
        val byId = finished(tasks, firstWeek(today, count), today).groupingBy { (_, task) -> task.projectId }.eachCount()
        return projects.filterNot(ProjectItem::deleted)
            .distinctBy(ProjectItem::id)
            .mapNotNull { project -> byId[project.id]?.let { StatsDigest.ProjectDone(project.id, project.name, it) } }
            .sortedByDescending(StatsDigest.ProjectDone::done)
    }

    // The Monday of the first of the last [count] weeks.
    private fun firstWeek(today: LocalDate, count: Int): LocalDate =
        GoalRules.periodStart(GoalHorizon.WEEK, today).minusWeeks((count - 1).coerceAtLeast(0).toLong())

    // The tasks finished from [first] to [today], each with the day it was finished.
    private fun finished(tasks: List<TaskItem>, first: LocalDate, today: LocalDate): List<Pair<LocalDate, TaskItem>> =
        tasks.filterNot(TaskItem::deleted)
            .mapNotNull { task -> task.completedDay?.let { it to task } }
            .filter { (day, _) -> !day.isBefore(first) && !day.isAfter(today) }

    private fun liveProjects(projects: List<ProjectItem>): Set<String> = projects.filterNot(ProjectItem::deleted).mapTo(HashSet(), ProjectItem::id)

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
        val from = firstWeek(today, weeks)
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
