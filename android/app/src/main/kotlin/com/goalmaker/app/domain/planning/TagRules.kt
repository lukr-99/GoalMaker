package com.goalmaker.app.domain.planning

import com.goalmaker.app.application.planning.TagItem

/**
 * Tags that belong to an area or a project (docs/lists.md, "Tags"): which ones a filter offers, and how
 * Settings groups them. A tag whose area or project is gone counts as for everything. Pinned by
 * contracts/vectors/tags.json.
 */
object TagRules {
    /** One group in Settings: the tags for everything ([place] null), an area's or a project's. */
    data class Group(val place: TagPlace?, val tags: List<TagItem>)

    /**
     * The tags a tag picker offers, in their order: every tag with nothing chosen; with [areaId], the
     * tags for everything and that area's; on a project's board ([projectId]), the tags for everything,
     * that project's and its area's. [projectAreas] maps each live project to its area.
     */
    fun offered(
        tags: List<TagItem>,
        areaIds: Set<String>,
        projectAreas: Map<String, String?>,
        areaId: String? = null,
        projectId: String? = null,
    ): List<TagItem> {
        if (areaId == null && projectId == null) return tags
        val area = areaId ?: projectId?.let(projectAreas::get)
        return tags.filter { tag ->
            when (val place = placeOf(tag, areaIds, projectAreas.keys)) {
                null -> true
                is TagPlace.Area -> place.id == area
                is TagPlace.Project -> place.id == projectId
            }
        }
    }

    /** Settings' groups: the tags for everything, then each area's in [areaIds] order, then each project's; empty ones left out. */
    fun groups(tags: List<TagItem>, areaIds: List<String>, projectIds: List<String>): List<Group> {
        val byPlace = tags.groupBy { placeOf(it, areaIds.toSet(), projectIds.toSet()) }
        val places = listOf<TagPlace?>(null) + areaIds.map(TagPlace::Area) + projectIds.map(TagPlace::Project)
        return places.mapNotNull { place -> byPlace[place]?.let { Group(place, it) } }
    }

    /** Where a tag belongs, or null when it is for everything (or its area or project is gone). */
    fun placeOf(tag: TagItem, areaIds: Set<String>, projectIds: Set<String>): TagPlace? = when {
        tag.areaId != null && tag.areaId in areaIds -> TagPlace.Area(tag.areaId)
        tag.projectId != null && tag.projectId in projectIds -> TagPlace.Project(tag.projectId)
        else -> null
    }
}
