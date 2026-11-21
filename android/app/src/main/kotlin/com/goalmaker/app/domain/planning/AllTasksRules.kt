package com.goalmaker.app.domain.planning

import com.goalmaker.app.application.planning.ArchiveRules
import com.goalmaker.app.application.planning.TaskItem
import com.goalmaker.app.application.planning.TaskState
import java.time.LocalDate

/**
 * The All tasks place (docs/all-tasks.md): every open task in one list, grouped by area, so a task filed
 * into an area without a day can always be found. Pinned by contracts/vectors/all-tasks.json.
 */
object AllTasksRules {
    /** One area's tasks; [areaId] null is the group of tasks without an area, which comes last. */
    data class Group(val areaId: String?, val tasks: List<TaskItem>)

    /** By date (the deadline, else the planned day), earliest first and undated last, then title, then id. */
    val BY_DATE: Comparator<TaskItem> =
        compareBy<TaskItem, LocalDate?>(nullsLast()) { it.deadline ?: it.plannedDate }
            .thenBy { ArchiveRules.fold(it.title) }
            .thenBy(TaskItem::id)

    /** The words of a search, folded the way [matches] compares them. */
    fun words(query: String): List<String> = query.split(' ').map(ArchiveRules::fold).filter(String::isNotEmpty)

    /** Whether every one of [words] is in the task's title or notes, ignoring case and accents. */
    fun matches(task: TaskItem, words: List<String>): Boolean {
        if (words.isEmpty()) return true
        val text = ArchiveRules.fold(task.title) + "\n" + ArchiveRules.fold(task.notes)
        return words.all { it in text }
    }

    /**
     * The open tasks that match [query], grouped by area in the order of [areaIds], the no-area group
     * last and empty groups left out. A project item shows only with [projectItems]; a task's group is
     * its own area, else its project's ([projectAreas]), and an area not in [areaIds] counts as none.
     */
    fun groups(
        tasks: List<TaskItem>,
        areaIds: List<String>,
        projectAreas: Map<String, String?>,
        query: String,
        projectItems: Boolean,
    ): List<Group> {
        val wanted = words(query)
        val known = areaIds.toSet()
        val shown = tasks.filter { task ->
            !task.deleted && task.state == TaskState.OPEN && (projectItems || task.projectId == null) && matches(task, wanted)
        }
        val byArea = shown.groupBy { task ->
            (task.areaId ?: task.projectId?.let(projectAreas::get))?.takeIf { it in known }
        }
        return (areaIds + listOf<String?>(null))
            .mapNotNull { area -> byArea[area]?.sortedWith(BY_DATE)?.let { Group(area, it) } }
    }
}
