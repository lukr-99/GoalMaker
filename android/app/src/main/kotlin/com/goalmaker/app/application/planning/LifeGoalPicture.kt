package com.goalmaker.app.application.planning

/**
 * A picture of a life goal (ADR 0018): the row that syncs. The file is `<owner id>/<id>.jpg` in the
 * life-goal-pictures bucket and in each device's picture cache.
 */
data class LifeGoalPicture(
    val id: String,
    val lifeGoalId: String,
    val width: Int,
    val height: Int,
    val position: Double = 0.0,
    val deleted: Boolean = false,
)
