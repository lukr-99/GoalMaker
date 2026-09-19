package com.goalmaker.app.application.planning

/** A goal to create when last period's goals are copied into a new period (docs/goals.md). */
data class GoalCopy(
    val title: String,
    val emoji: String?,
    val mode: String,
    val target: Double?,
    val unit: String?,
    val parentId: String?,
)
