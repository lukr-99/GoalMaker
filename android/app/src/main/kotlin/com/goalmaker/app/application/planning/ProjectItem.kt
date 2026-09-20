package com.goalmaker.app.application.planning

/**
 * A project as the screens and rules see it (docs/projects.md): where its code lives, which area it
 * belongs to, and whether it is active, paused or done.
 */
data class ProjectItem(
    val id: String,
    val name: String,
    val description: String = "",
    val areaId: String? = null,
    val status: String = ProjectRules.ACTIVE,
    val repositoryUrl: String? = null,
    val localFolder: String? = null,
    val notes: String = "",
    val position: Double = 0.0,
    val deleted: Boolean = false,
)
