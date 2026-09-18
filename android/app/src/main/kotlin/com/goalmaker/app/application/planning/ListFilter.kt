package com.goalmaker.app.application.planning

/**
 * What the lists are narrowed to (spec, story 9; docs/lists.md): one area, one tag, both or neither.
 * It applies before the list rules, so every list and its summary show the same slice, and it stays
 * when the owner switches lists. Pinned by the 'filter' cases in contracts/vectors/lists.json.
 */
data class ListFilter(val areaId: String? = null, val tagId: String? = null) {
    val isEmpty: Boolean get() = areaId == null && tagId == null

    /** Whether [task], linked to the tags in [tagIds], stays in the lists. */
    fun matches(task: TaskItem, tagIds: Set<String>): Boolean =
        (areaId == null || task.areaId == areaId) && (tagId == null || tagId in tagIds)

    /** The tasks that stay, in their order; [tagLinks] maps a task id to the ids of its tags. */
    fun apply(tasks: List<TaskItem>, tagLinks: Map<String, Set<String>>): List<TaskItem> =
        if (isEmpty) tasks else tasks.filter { matches(it, tagLinks[it.id].orEmpty()) }

    companion object {
        val NONE = ListFilter()
    }
}
