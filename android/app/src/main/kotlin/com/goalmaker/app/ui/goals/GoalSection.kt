package com.goalmaker.app.ui.goals

import com.goalmaker.app.application.planning.GoalHorizon
import java.time.LocalDate

/**
 * One period on the Goals screen: its goals, and [canCopy] when it has none yet but the period before
 * had some to copy (docs/goals.md). [next] marks the coming week, there for planning ahead.
 */
data class GoalSection(
    val horizon: GoalHorizon,
    val start: LocalDate,
    val rows: List<GoalRow>,
    val canCopy: Boolean,
    val next: Boolean = false,
)
