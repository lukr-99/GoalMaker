package com.goalmaker.app.ui.habits

import com.goalmaker.app.application.planning.GoalItem
import com.goalmaker.app.application.planning.GoalRules
import com.goalmaker.app.application.planning.HabitData
import com.goalmaker.app.application.planning.HabitItem
import com.goalmaker.app.application.planning.HabitPeriodState
import com.goalmaker.app.application.planning.HabitRules
import java.time.LocalDate

/** What the Habits screen and Today's ring row show (docs/habits.md, design spec, Today). */
object HabitBoard {
    /** Streak lengths that get confetti. */
    val MILESTONES = setOf(7, 14, 30, 50, 100, 200, 365, 500, 1000)

    // Weeks of heatmap kept per habit; the screen shows the last ones that fit, and never fewer than
    // MIN_WEEKS, so a young habit still has a map to fill.
    private const val HEAT_WEEKS = 26
    private const val MIN_WEEKS = 8

    fun build(data: HabitData, goals: List<GoalItem>, today: LocalDate): HabitsUiState {
        val goalTitles = goals.associate { it.id to it.title }
        val rows = data.habits.map { row(it, data, today, goalTitles, heat = true) }
        val active = rows.filterNot { it.habit.archived }
        val served = data.habits.mapNotNull(HabitItem::goalId).toSet()
        return HabitsUiState(
            loaded = true,
            today = today,
            active = active,
            archived = rows.filter { it.habit.archived },
            // Goals a habit can serve: not dropped and not over yet, or already served.
            goals = goals.filter { goal ->
                goal.id in served || (goal.status != GoalRules.DROPPED && !GoalRules.periodEnd(goal.horizon, goal.periodStart).isBefore(today))
            },
            milestones = milestones(active),
        )
    }

    /** Today's habits for the ring row: active, not paused, and due today. */
    fun today(data: HabitData, today: LocalDate): List<HabitRow> = data.habits
        .filter { !it.archived && HabitRules.isDue(it, today) && !today.isBefore(it.startsOn) }
        .map { row(it, data, today, emptyMap(), heat = false) }
        .filterNot(HabitRow::paused)

    /** "habit id:streak" of each habit whose streak, with today's period met, is a milestone. */
    fun milestones(rows: List<HabitRow>): Set<String> = rows
        .filter { it.streak in MILESTONES && it.state == HabitPeriodState.MET }
        .map { "${it.habit.id}:${it.streak}" }
        .toSet()

    private fun monday(day: LocalDate): LocalDate = day.minusDays(day.dayOfWeek.value - 1L)

    private fun row(habit: HabitItem, data: HabitData, today: LocalDate, goalTitles: Map<String, String>, heat: Boolean): HabitRow {
        val checkins = data.checkinsOf(habit.id)
        val pauses = data.pausesOf(habit.id)
        val start = HabitRules.periodStart(habit, today)
        val end = HabitRules.periodEnd(habit, start)
        val inPeriod = checkins.filter { !it.day.isBefore(start) && !it.day.isAfter(end) }
        // The map starts at the Monday of the habit's first week, at most HEAT_WEEKS back and at least
        // MIN_WEEKS wide.
        val heatStart = minOf(
            monday(today.minusWeeks(MIN_WEEKS - 1L)),
            maxOf(monday(habit.startsOn), monday(today.minusWeeks(HEAT_WEEKS - 1L))),
        )
        return HabitRow(
            habit = habit,
            ring = HabitRules.ring(habit, today, checkins),
            streak = HabitRules.streak(habit, today, checkins, pauses),
            state = HabitRules.state(habit, start, today, checkins, pauses),
            value = checkins.firstOrNull { it.day == today && !it.skipped }?.value ?: 0.0,
            met = inPeriod.count { HabitRules.dayMet(habit, it) },
            skipped = inPeriod.any { it.skipped },
            paused = pauses.any { !it.from.isAfter(today) && (it.until == null || !it.until.isBefore(today)) },
            goalTitle = habit.goalId?.let(goalTitles::get),
            heatStart = heatStart,
            heat = if (heat) {
                generateSequence(heatStart) { it.plusDays(1) }.takeWhile { !it.isAfter(today) }
                    .map { HabitRules.heat(habit, it, checkins, pauses) }
                    .toList()
            } else {
                emptyList()
            },
        )
    }
}
