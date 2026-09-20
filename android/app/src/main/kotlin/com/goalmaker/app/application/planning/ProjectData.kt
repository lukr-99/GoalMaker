package com.goalmaker.app.application.planning

/** Every project and milestone that isn't deleted, as [ProjectList] reads them. */
data class ProjectData(
    val projects: List<ProjectItem> = emptyList(),
    val milestones: List<ProjectMilestone> = emptyList(),
) {
    fun milestonesOf(projectId: String): List<ProjectMilestone> = milestones.filter { it.projectId == projectId }

    fun find(id: String?): ProjectItem? = projects.firstOrNull { it.id == id }
}
