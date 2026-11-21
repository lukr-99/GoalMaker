package com.goalmaker.app.ui.areas

import com.goalmaker.app.application.planning.AreaItem
import com.goalmaker.app.application.planning.ProjectItem
import com.goalmaker.app.application.planning.TagItem
import com.goalmaker.app.domain.planning.TagRules

/**
 * The areas in their order, the tags oldest first, the palette's color ids for the color picker, and the
 * projects a tag can belong to (docs/lists.md, "Tags").
 */
data class AreasUiState(
    val areas: List<AreaItem> = emptyList(),
    val tags: List<TagItem> = emptyList(),
    val palette: List<String> = emptyList(),
    val projects: List<ProjectItem> = emptyList(),
) {
    /** The tags grouped the way Settings lists them: for everything, then by area, then by project. */
    val tagGroups: List<TagRules.Group> get() = TagRules.groups(tags, areas.map { it.id }, projects.map { it.id })

    /** The areas in use, which the arrows reorder. */
    val active: List<AreaItem> get() = areas.filterNot(AreaItem::archived)

    /** The archived areas, each with a way back. */
    val archived: List<AreaItem> get() = areas.filter(AreaItem::archived)
}
