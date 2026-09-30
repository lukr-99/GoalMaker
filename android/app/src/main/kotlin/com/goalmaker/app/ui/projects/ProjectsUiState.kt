package com.goalmaker.app.ui.projects

import com.goalmaker.app.application.planning.ProjectColumn
import com.goalmaker.app.application.planning.ProjectItem
import com.goalmaker.app.application.planning.ProjectMilestone
import com.goalmaker.app.application.planning.ProjectRules
import com.goalmaker.app.application.planning.TaskItem
import com.goalmaker.app.domain.settings.BoardView

/**
 * The Projects screen: the owner's projects, and the board of the one being looked at.
 * [openCounts] is how many items each project still has waiting, by project id, so the picker
 * says where the work is without opening every board. [madeBy] is what the who-made-it switch is set
 * to, and the board holds only the items it shows. [archived] are the done items that left the board,
 * most recently finished first. [view] and [collapsed] are how this phone shows the board.
 */
data class ProjectsUiState(
    val loaded: Boolean = false,
    val projects: List<ProjectItem> = emptyList(),
    val selected: ProjectItem? = null,
    val board: List<ProjectColumn> = emptyList(),
    val archived: List<TaskItem> = emptyList(),
    val milestones: List<ProjectMilestone> = emptyList(),
    val openCounts: Map<String, Int> = emptyMap(),
    val madeBy: String = ProjectRules.EVERYONE,
    val view: BoardView = BoardView.COLUMNS,
    val collapsed: Set<String> = setOf(ProjectRules.DONE),
) {
    /** How many items are in the columns that are not done. */
    val open: Int get() = board.filterNot { it.column == "done" }.sumOf { it.items.size }
}
