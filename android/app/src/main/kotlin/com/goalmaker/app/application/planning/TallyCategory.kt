package com.goalmaker.app.application.planning

/**
 * A kind of time Tally sorts into (docs/tally.md): a shipped default, whose [id] is a stable word like
 * `coding`, or one of the owner's own, whose [id] is its row's. [color] is from the area palette.
 */
data class TallyCategory(
    val id: String,
    val name: String,
    val color: String,
    val emoji: String? = null,
    val position: Int = 0,
)
