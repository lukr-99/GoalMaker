package com.goalmaker.app.application.planning

import java.time.LocalDate

/**
 * A goal as the screens and rules see it (docs/goals.md). [mode] is done, tasks or number; [status]
 * is open, done or dropped; [target] and [unit] belong to a numeric goal.
 */
data class GoalItem(
    val id: String,
    val title: String,
    val horizon: GoalHorizon,
    val periodStart: LocalDate,
    val mode: String = GoalRules.MODE_DONE,
    val status: String = GoalRules.OPEN,
    val emoji: String? = null,
    val parentId: String? = null,
    val target: Double? = null,
    val unit: String? = null,
    val completedAt: String? = null,
    val position: Double = 0.0,
    val deleted: Boolean = false,
)
