package com.goalmaker.app.ui.lists

import com.goalmaker.app.application.planning.AreaItem
import com.goalmaker.app.application.planning.ProjectItem
import com.goalmaker.app.application.planning.TagItem

/** What a task row and the filter need besides the task itself, read once per change instead of per row. */
data class RowContext(
    val areas: List<AreaItem>,
    val tags: List<TagItem>,
    val reminded: Set<String>,
    val projects: List<ProjectItem>,
)
