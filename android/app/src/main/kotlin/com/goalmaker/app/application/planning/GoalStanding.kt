package com.goalmaker.app.application.planning

/** A goal's [pace], and for one counted by tasks or a number that is behind, the amount it is [behind] by. */
data class GoalStanding(
    val pace: GoalPace,
    val behind: Double? = null,
)
