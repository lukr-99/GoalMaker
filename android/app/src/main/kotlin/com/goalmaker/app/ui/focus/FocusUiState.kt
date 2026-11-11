package com.goalmaker.app.ui.focus

import com.goalmaker.app.application.planning.AreaItem
import com.goalmaker.app.application.planning.TaskItem

data class FocusUiState(
    val items: List<TaskItem> = emptyList(),
    val areas: List<AreaItem> = emptyList(),
    /** All open tasks not on the board, for the add sheet. */
    val candidates: List<TaskItem> = emptyList(),
    val showAddSheet: Boolean = false,
)
