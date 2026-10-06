package com.goalmaker.app.ui.projects

import com.goalmaker.app.application.planning.ProjectColumn
import com.goalmaker.app.application.planning.ProjectItem
import com.goalmaker.app.application.planning.ProjectMilestone
import com.goalmaker.app.application.planning.ProjectRules
import com.goalmaker.app.application.planning.TaskItem
import com.goalmaker.app.domain.settings.BoardView
import com.goalmaker.app.ui.lists.FilterChoices

/**
 * The Projects screen: the owner's projects, and the board of the one being looked at.
 * [openCounts] is how many items each project still has waiting, by project id, so the picker
 * says where the work is without opening every board. [madeBy] is what the who-made-it switch is set
 * to, and the board holds only the items it shows. [filter] is the area and tag filter, which narrows
 * [projects] and the board; [anyProject] says whether there are projects at all, filtered away or not.
 * [archived] are the done items that left the board, most recently finished first. [view] and
 * [collapsed] are how this phone shows the board. [keys] are the item keys of every project, filtered
 * away or not, by project id, so the form can suggest a free one; [query] is what the board's search
 * holds, words or an item's id (GM-12, #12), and the board and [archived] hold only what it finds.
 */
data class ProjectsUiState(
    val loaded: Boolean = false,
    val projects: List<ProjectItem> = emptyList(),
    val anyProject: Boolean = false,
    val selected: ProjectItem? = null,
    val board: List<ProjectColumn> = emptyList(),
    val archived: List<TaskItem> = emptyList(),
    val milestones: List<ProjectMilestone> = emptyList(),
    val openCounts: Map<String, Int> = emptyMap(),
    val madeBy: String = ProjectRules.EVERYONE,
    val filter: FilterChoices = FilterChoices(),
    val view: BoardView = BoardView.COLUMNS,
    val collapsed: Set<String> = setOf(ProjectRules.DONE),
    val keys: Map<String, String> = emptyMap(),
    val query: String = "",
) {
    /** The id an item of the project on show reads by, GM-12, or null until the server has numbered it. */
    fun itemIdOf(task: TaskItem): String? = ProjectRules.itemIdOf(task, selected)

    /** The keys the projects other than [exceptId] read by. */
    fun otherKeys(exceptId: String? = null): List<String> = keys.filterKeys { it != exceptId }.values.toList()

    /** How many items are in the columns that are not done. */
    val open: Int get() = board.filterNot { it.column == "done" }.sumOf { it.items.size }
}
