package com.goalmaker.app.ui.areas

import com.goalmaker.app.application.planning.AreaItem
import com.goalmaker.app.application.planning.TagItem

/** The areas in their order, the tags oldest first, and the palette's color ids for the color picker. */
data class AreasUiState(
    val areas: List<AreaItem> = emptyList(),
    val tags: List<TagItem> = emptyList(),
    val palette: List<String> = emptyList(),
)
