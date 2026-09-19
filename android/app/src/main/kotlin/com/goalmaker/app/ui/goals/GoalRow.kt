package com.goalmaker.app.ui.goals

import com.goalmaker.app.application.planning.GoalItem
import com.goalmaker.app.application.planning.GoalProgress

/** One goal on the Goals screen: where it stands, the goal it serves, and how deep it sits in the cascade. */
data class GoalRow(
    val goal: GoalItem,
    val progress: GoalProgress,
    val parentTitle: String? = null,
    val depth: Int = 0,
)
