package com.goalmaker.app.application.planning

/** Where a goal stands: [value] of [target], [fraction] (0 to 1) for its ring or bar, and whether it is [hit]. */
data class GoalProgress(
    val value: Double,
    val target: Double,
    val fraction: Double,
    val hit: Boolean,
)
