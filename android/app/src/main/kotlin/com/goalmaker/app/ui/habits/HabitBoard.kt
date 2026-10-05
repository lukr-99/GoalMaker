package com.goalmaker.app.ui.habits

import com.goalmaker.app.application.planning.GoalItem
import com.goalmaker.app.application.planning.GoalRules
import com.goalmaker.app.application.planning.HabitData
import com.goalmaker.app.application.planning.HabitGroup
import com.goalmaker.app.application.planning.HabitItem
import com.goalmaker.app.application.planning.HabitPeriodState
import com.goalmaker.app.application.planning.HabitRules
import com.goalmaker.app.application.planning.HabitStanding
import java.time.LocalDate

/** What the Habits screen and Today's ring row show (docs/habits.md, design spec, Today). */
object HabitBoard {
    /** Streak lengths that get confetti. */
    val MILESTONES = setOf(7, 14, 30, 50, 100, 200, 365, 500, 1000)

    // Weeks of heatmap kept per habit; the screen shows the last ones that fit, and never fewer than
    // MIN_WEEKS, so a young habit still has a map to fill.
    private const val HEAT_WEEKS = 26
    private const val MIN_WEEKS = 8

    /** Days in the week's dots on a card: today and the six before it. */
    const val WEEK_DAYS = 7

    fun build(data: HabitData, goals: List<GoalItem>, today: LocalDate, hideDone: Boolean = false): HabitsUiState {
        val goalTitles = goals.associate { it.id to it.title }
        val rows = data.habits.map { row(it, data, today, goalTitles, heat = true) }
        val active = rows.filterNot { it.habit.archived }
        val served = data.habits.mapNotNull(HabitItem::goalId).toSet()
        return HabitsUiState(
            loaded = true,
            today = today,
            active = active,
            sections = sections(active, hideDone),
            summary = summary(active),
            hideDone = hideDone,
            archived = rows.filter { it.habit.archived },
            // Goals a habit can serve: not dropped and not over yet, or already served.
            goals = goals.filter { goal ->
                goal.id in served || (goal.status != GoalRules.DROPPED && !GoalRules.periodEnd(goal.horizon, goal.periodStart).isBefore(today))
            },
            milestones = milestones(active),
        )
    }

    /** Today's habits for the ring row: due today and not kept off Today (contracts/vectors/habits.json, onToday). */
    fun today(data: HabitData, today: LocalDate): List<HabitRow> = data.habits
        .filter { HabitRules.onToday(it, today, data.pausesOf(it.id)) }
        .map { row(it, data, today, emptyMap(), heat = false) }

    /**
     * Every habit due today, the ones kept off Today too: what the Places hub counts. The calendar asks
     * for another day's with [onDay], read as of that day.
     */
    fun due(data: HabitData, today: LocalDate, onDay: Boolean = false): List<HabitRow> = data.habits
        .filter { HabitRules.dueToday(it, today, data.pausesOf(it.id)) }
        .map { row(it, data, today, emptyMap(), heat = false).copy(onDay = onDay) }

    /**
     * The Habits screen's groups in order, Every day, Weekly and Limits (contracts/vectors/habits.json,
     * groups), each without its done habits when [hideDone] is on; a group with no habits is left out.
     */
    fun sections(rows: List<HabitRow>, hideDone: Boolean): List<HabitSection> = HabitGroup.entries.mapNotNull { group ->
        val all = rows.filter { it.group == group }
        if (all.isEmpty()) null else HabitSection(group, all.filterNot { hideDone && it.done }, all.size)
    }

    /** What is shown of [rows] while Hide done is [hideDone]: the done ones go, the rest stay in place. */
    fun shown(rows: List<HabitRow>, hideDone: Boolean): List<HabitRow> = if (hideDone) rows.filterNot(HabitRow::done) else rows

    /** Today's count, how far the day has got and the longest streak, for the summary card. */
    fun summary(rows: List<HabitRow>): HabitSummary {
        // A failed habit asked something of today and didn't get it, so it counts against the day.
        val asking = rows.filter { it.standing == HabitStanding.DONE || it.standing == HabitStanding.LEFT || it.standing == HabitStanding.FAILED }
        return HabitSummary(
            done = asking.count(HabitRow::done),
            total = asking.size,
            share = if (asking.isEmpty()) 0.0 else asking.sumOf { if (it.done) 1.0 else it.ring ?: 0.0 } / asking.size,
            best = rows.filter { it.streak > 0 }.maxByOrNull(HabitRow::streak),
        )
    }

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
            // A limit shows what it has had: the day's, or the week's or month's so far.
            value = if (HabitRules.isLimit(habit)) {
                HabitRules.used(habit, today, checkins)
            } else {
                checkins.firstOrNull { it.day == today && !it.skipped }?.value ?: 0.0
            },
            met = inPeriod.count { HabitRules.dayMet(habit, it) },
            skipped = inPeriod.any { it.skipped },
            // A skip comes before a fail, as the standing reads them.
            failed = inPeriod.none { it.skipped } && inPeriod.any { it.failed },
            paused = pauses.any { !it.from.isAfter(today) && (it.until == null || !it.until.isBefore(today)) },
            goalTitle = habit.goalId?.let(goalTitles::get),
            heatStart = heatStart,
            standing = HabitRules.standing(habit, today, checkins, pauses),
            dots = (WEEK_DAYS - 1 downTo 0).map { back -> HabitRules.dot(habit, today.minusDays(back.toLong()), today, checkins, pauses) },
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
