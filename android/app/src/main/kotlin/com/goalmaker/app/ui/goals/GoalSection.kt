package com.goalmaker.app.ui.goals

import com.goalmaker.app.application.planning.GoalHorizon
import java.time.LocalDate

/**
 * One period on the Goals screen, a rung of the ladder: its goals, the ones that need you first, and
 * [canCopy] when it has none yet but the period before had some to copy (docs/goals.md). [next] marks
 * the coming week, there for planning ahead.
 */
data class GoalSection(
    val horizon: GoalHorizon,
    val start: LocalDate,
    val rows: List<GoalRow>,
    val canCopy: Boolean,
    val next: Boolean = false,
) {
    /** How many of its goals are hit. */
    val hits: Int get() = rows.count { it.progress.hit }

    /** Its ring on the dashboard: the mean of its goals' fractions, 0 with none. */
    val fraction: Double get() = if (rows.isEmpty()) 0.0 else rows.sumOf { it.progress.fraction } / rows.size
}
