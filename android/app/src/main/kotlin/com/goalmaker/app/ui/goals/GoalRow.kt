package com.goalmaker.app.ui.goals

import com.goalmaker.app.application.planning.GoalItem
import com.goalmaker.app.application.planning.GoalPace
import com.goalmaker.app.application.planning.GoalProgress
import com.goalmaker.app.application.planning.GoalStanding

/**
 * One goal on the Goals screen: where it stands and its [standing] against the period, the goal it
 * feeds, and for a numeric goal the [quickAmount] its quick log adds (null: nothing logged yet).
 */
data class GoalRow(
    val goal: GoalItem,
    val progress: GoalProgress,
    val parentTitle: String? = null,
    val standing: GoalStanding = GoalStanding(GoalPace.ON_TRACK),
    val quickAmount: Double? = null,
)
