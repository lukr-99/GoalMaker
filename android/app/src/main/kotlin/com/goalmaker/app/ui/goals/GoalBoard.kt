package com.goalmaker.app.ui.goals

import com.goalmaker.app.application.planning.GoalEntryItem
import com.goalmaker.app.application.planning.GoalHorizon
import com.goalmaker.app.application.planning.GoalItem
import com.goalmaker.app.application.planning.GoalRules
import com.goalmaker.app.application.planning.HabitData
import com.goalmaker.app.application.planning.TaskItem
import java.time.LocalDate

/**
 * What the Goals screen and Today show (docs/goals.md): the current year, month, week and day, next
 * week for planning ahead, and the same goals as the cascade. Dropped goals stay off both. Check-ins of
 * habits serving a numeric goal in its unit count like logged amounts (docs/habits.md).
 */
object GoalBoard {
    fun build(
        all: List<GoalItem>,
        entries: List<GoalEntryItem>,
        tasks: List<TaskItem>,
        today: LocalDate,
        showTree: Boolean = false,
        habits: HabitData = HabitData(),
    ): GoalsUiState {
        val byId = all.associateBy(GoalItem::id)
        val entriesByGoal = entries.groupBy(GoalEntryItem::goalId)
        val tasksByGoal = tasks.filter { it.goalId != null }.groupBy { it.goalId!! }
        fun row(goal: GoalItem, depth: Int = 0) = GoalRow(
            goal = goal,
            progress = GoalRules.progressOf(goal, tasksByGoal[goal.id].orEmpty(), entriesByGoal[goal.id].orEmpty(), habits),
            parentTitle = goal.parentId?.let(byId::get)?.title,
            depth = depth,
        )
        val week = GoalRules.periodStart(GoalHorizon.WEEK, today)
        val periods = listOf(
            GoalHorizon.YEAR to GoalRules.periodStart(GoalHorizon.YEAR, today),
            GoalHorizon.MONTH to GoalRules.periodStart(GoalHorizon.MONTH, today),
            GoalHorizon.WEEK to week,
            GoalHorizon.DAY to today,
            GoalHorizon.WEEK to week.plusWeeks(1),
        )
        fun kept(goal: GoalItem, horizon: GoalHorizon, start: LocalDate) =
            goal.horizon == horizon && goal.periodStart == start && goal.status != GoalRules.DROPPED
        val sections = periods.mapIndexed { index, (horizon, start) ->
            val own = all.filter { kept(it, horizon, start) }
            // A new week, month or year with no goals yet can start from the last one's (story 35).
            val previous = GoalRules.periodStart(horizon, start.minusDays(1))
            val canCopy = own.isEmpty() && horizon != GoalHorizon.DAY && all.any { kept(it, horizon, previous) }
            GoalSection(horizon, start, own.map { row(it) }, canCopy, next = index == periods.lastIndex)
        }

        // The cascade: the shown goals, each under the goal it serves when that one is shown too.
        val shown = sections.flatMap { section -> section.rows.map(GoalRow::goal) }
        val shownIds = shown.map(GoalItem::id).toSet()
        val children = shown.filter { it.parentId in shownIds }.groupBy { it.parentId!! }
        val tree = mutableListOf<GoalRow>()
        fun walk(goal: GoalItem, depth: Int) {
            tree += row(goal, depth)
            children[goal.id].orEmpty().forEach { walk(it, depth + 1) }
        }
        shown.filter { it.parentId !in shownIds }.forEach { walk(it, 0) }

        return GoalsUiState(
            loaded = true,
            today = today,
            sections = sections,
            tree = tree,
            showTree = showTree,
            goals = all,
            hits = sections.flatMap(GoalSection::rows).filter { it.progress.hit }.map { it.goal.id }.toSet(),
        )
    }

    /** This week's goals with their progress, for Today (design spec, Today). */
    fun thisWeek(all: List<GoalItem>, entries: List<GoalEntryItem>, tasks: List<TaskItem>, today: LocalDate, habits: HabitData = HabitData()): List<GoalRow> =
        build(all, entries, tasks, today, habits = habits).sections.first { it.horizon == GoalHorizon.WEEK && !it.next }.rows

}
