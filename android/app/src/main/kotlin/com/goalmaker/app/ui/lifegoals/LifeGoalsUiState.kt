package com.goalmaker.app.ui.lifegoals

/**
 * The Life goals place (docs/life-goals.md): the open ones in the owner's order, the achieved and
 * dropped ones below, and [pictureVersion], which moves when a picture file arrives or goes.
 */
data class LifeGoalsUiState(
    val loaded: Boolean = false,
    val open: List<LifeGoalRow> = emptyList(),
    val closed: List<LifeGoalRow> = emptyList(),
    val pictureVersion: Int = 0,
) {
    val empty: Boolean get() = open.isEmpty() && closed.isEmpty()
}
