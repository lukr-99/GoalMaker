package com.goalmaker.app.application.planning

/**
 * An area as lists and chips show it; [colorId] names a color in the area palette (themes.json). An
 * [archived] area keeps its tasks and chips but leaves the pickers and filters (docs/lists.md).
 */
data class AreaItem(
    val id: String,
    val name: String,
    val colorId: String,
    val emoji: String?,
    val archived: Boolean = false,
)
