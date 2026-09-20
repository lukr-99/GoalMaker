package com.goalmaker.app.application.planning

/** One of a project's milestones, like M0 to M6 (docs/projects.md). */
data class ProjectMilestone(
    val id: String,
    val projectId: String,
    val name: String,
    val position: Double = 0.0,
    val deleted: Boolean = false,
)
