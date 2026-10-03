package com.goalmaker.app.application.planning

/**
 * What a place is narrowed to (spec, story 9; docs/lists.md): one area, one tag, both or neither.
 * It applies before the list rules, so every list and its summary show the same slice, and it stays
 * when the owner switches lists. The Projects board, the calendar and the archive narrow by the same
 * rule. A task without an area of its own counts as being in its project's area. Pinned by the
 * 'filter' cases in contracts/vectors/lists.json.
 */
data class ListFilter(val areaId: String? = null, val tagId: String? = null) {
    val isEmpty: Boolean get() = areaId == null && tagId == null

    /** Whether [task], linked to the tags in [tagIds], stays; [projectAreaId] is its project's area. */
    fun matches(task: TaskItem, tagIds: Set<String>, projectAreaId: String? = null): Boolean =
        (areaId == null || (task.areaId ?: projectAreaId) == areaId) && (tagId == null || tagId in tagIds)

    /**
     * The tasks that stay, in their order; [tagLinks] maps a task id to the ids of its tags, and
     * [projectAreas] a project id to its area.
     */
    fun apply(
        tasks: List<TaskItem>,
        tagLinks: Map<String, Set<String>>,
        projectAreas: Map<String, String?> = emptyMap(),
    ): List<TaskItem> = if (isEmpty) tasks else tasks.filter { keeps(it, tagLinks, projectAreas) }

    /** [matches] with the task's tags and its project's area looked up. */
    fun keeps(task: TaskItem, tagLinks: Map<String, Set<String>>, projectAreas: Map<String, String?> = emptyMap()): Boolean =
        matches(task, tagLinks[task.id].orEmpty(), task.projectId?.let(projectAreas::get))

    /**
     * Whether a project in [projectAreaId] stays in the project list: always without a filter; else
     * when its own area is the one chosen and no tag is, or when the filter keeps one of its [items].
     */
    fun keepsProject(projectAreaId: String?, items: List<TaskItem>, tagLinks: Map<String, Set<String>>): Boolean =
        isEmpty ||
            (tagId == null && projectAreaId == areaId) ||
            items.any { matches(it, tagLinks[it.id].orEmpty(), projectAreaId) }

    companion object {
        val NONE = ListFilter()
    }
}
