package com.goalmaker.app.ui.areas

import com.goalmaker.app.application.planning.AreaItem
import com.goalmaker.app.application.planning.TagItem

/** The areas in their order, the tags oldest first, and the palette's color ids for the color picker. */
data class AreasUiState(
    val areas: List<AreaItem> = emptyList(),
    val tags: List<TagItem> = emptyList(),
    val palette: List<String> = emptyList(),
) {
    /** The areas in use, which the arrows reorder. */
    val active: List<AreaItem> get() = areas.filterNot(AreaItem::archived)

    /** The archived areas, each with a way back. */
    val archived: List<AreaItem> get() = areas.filter(AreaItem::archived)
}
