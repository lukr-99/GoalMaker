package com.goalmaker.app.ui.goals

import com.goalmaker.app.application.planning.GoalEntryItem
import com.goalmaker.app.application.planning.GoalHorizon
import com.goalmaker.app.application.planning.GoalItem
import com.goalmaker.app.application.planning.GoalRules
import com.goalmaker.app.application.planning.HabitData
import com.goalmaker.app.application.planning.TaskItem
import java.time.LocalDate

/**
 * What the Goals screen and Today show (docs/goals.md): the current year, month, week and day, each
 * with the goals that need you first, and next week for planning ahead. Dropped goals stay off. Check-ins
 * of habits serving a numeric goal in its unit count like logged amounts (docs/habits.md). A [filter]
 * shows one horizon; a [picked] goal lights its chain, what it feeds and what feeds it.
 */
object GoalBoard {
    fun build(
        all: List<GoalItem>,
        entries: List<GoalEntryItem>,
        tasks: List<TaskItem>,
        today: LocalDate,
        habits: HabitData = HabitData(),
        filter: GoalHorizon? = null,
        picked: String? = null,
    ): GoalsUiState {
        val byId = all.associateBy(GoalItem::id)
        val entriesByGoal = entries.groupBy(GoalEntryItem::goalId)
        val tasksByGoal = tasks.filter { it.goalId != null }.groupBy { it.goalId!! }
        fun row(goal: GoalItem): GoalRow {
            val own = entriesByGoal[goal.id].orEmpty()
            val progress = GoalRules.progressOf(goal, tasksByGoal[goal.id].orEmpty(), own, habits)
            return GoalRow(
                goal = goal,
                progress = progress,
                parentTitle = goal.parentId?.let(byId::get)?.title,
                standing = GoalRules.standing(goal, progress, today),
                quickAmount = if (goal.mode == GoalRules.MODE_NUMBER) GoalRules.quickAmount(own) else null,
            )
        }
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
            val rows = GoalRules.byPace(own.map(::row)) { it.standing.pace }
            GoalSection(horizon, start, rows, canCopy, next = index == periods.lastIndex)
        }

        val shown = sections.flatMap(GoalSection::rows)
        val pickedRow = picked?.let { id -> shown.firstOrNull { it.goal.id == id } }
        val lit = pickedRow?.let { GoalRules.chain(all.filter { it.status != GoalRules.DROPPED }, it.goal.id) }.orEmpty()

        return GoalsUiState(
            loaded = true,
            today = today,
            sections = sections,
            goals = all,
            hits = shown.filter { it.progress.hit }.map { it.goal.id }.toSet(),
            filter = filter,
            picked = pickedRow,
            chain = lit,
        )
    }

    /** This week's goals with their progress, for Today (design spec, Today). */
    fun thisWeek(all: List<GoalItem>, entries: List<GoalEntryItem>, tasks: List<TaskItem>, today: LocalDate, habits: HabitData = HabitData()): List<GoalRow> =
        build(all, entries, tasks, today, habits = habits).sections.first { it.horizon == GoalHorizon.WEEK && !it.next }.rows
}
