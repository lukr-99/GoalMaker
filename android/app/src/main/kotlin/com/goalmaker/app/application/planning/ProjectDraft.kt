package com.goalmaker.app.application.planning

/** What a new project or an edit says (docs/projects.md). */
data class ProjectDraft(
    val name: String,
    val description: String = "",
    val areaId: String? = null,
    val status: String = ProjectRules.ACTIVE,
    val repositoryUrl: String? = null,
    val localFolder: String? = null,
    val notes: String = "",
)
