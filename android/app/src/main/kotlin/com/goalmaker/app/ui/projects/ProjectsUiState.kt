package com.goalmaker.app.ui.projects

import com.goalmaker.app.application.planning.ProjectColumn
import com.goalmaker.app.application.planning.ProjectItem
import com.goalmaker.app.application.planning.ProjectMilestone

/**
 * The Projects screen: the owner's projects, and the board of the one being looked at.
 * [openCounts] is how many items each project still has waiting, by project id, so the picker
 * says where the work is without opening every board.
 */
data class ProjectsUiState(
    val loaded: Boolean = false,
    val projects: List<ProjectItem> = emptyList(),
    val selected: ProjectItem? = null,
    val board: List<ProjectColumn> = emptyList(),
    val milestones: List<ProjectMilestone> = emptyList(),
    val openCounts: Map<String, Int> = emptyMap(),
) {
    /** How many items are in the columns that are not done. */
    val open: Int get() = board.filterNot { it.column == "done" }.sumOf { it.items.size }
}
