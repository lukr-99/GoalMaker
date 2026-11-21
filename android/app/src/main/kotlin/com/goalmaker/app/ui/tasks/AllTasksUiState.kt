package com.goalmaker.app.ui.tasks

import com.goalmaker.app.application.planning.AreaItem
import com.goalmaker.app.application.planning.ProjectItem
import com.goalmaker.app.domain.planning.AllTasksRules
import com.goalmaker.app.ui.lists.FilterChoices

/** What the All tasks place shows (docs/all-tasks.md). */
data class AllTasksUiState(
    /** The open tasks that match, grouped by area; null until the replica has been read. */
    val groups: List<AllTasksRules.Group>? = null,
    /** Every area by id, archived ones too, for the group headers. */
    val areas: Map<String, AreaItem> = emptyMap(),
    /** The projects that are still there, by id, for the chips. */
    val projects: Map<String, ProjectItem> = emptyMap(),
    val choices: FilterChoices = FilterChoices(),
    val query: String = "",
    /** Whether project items show too; off by default, since their boards already list them. */
    val projectItems: Boolean = false,
)
