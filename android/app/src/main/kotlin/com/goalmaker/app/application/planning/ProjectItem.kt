package com.goalmaker.app.application.planning

/**
 * A project as the screens and rules see it (docs/projects.md): where its code lives, which area it
 * belongs to, whether it is active, paused or done, and [archiveAfterDays], the days a done item stays
 * on its board (null: until it is archived by hand). [itemKey] is what its items' ids start with, GM
 * in GM-12, or null for none (docs/projects.md, "Item ids").
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
    val archiveAfterDays: Int? = ProjectRules.ARCHIVE_AFTER_DAYS,
    val itemKey: String? = null,
)
