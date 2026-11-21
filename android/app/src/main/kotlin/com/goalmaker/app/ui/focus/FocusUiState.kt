package com.goalmaker.app.ui.focus

import com.goalmaker.app.application.planning.AreaItem
import com.goalmaker.app.application.planning.TaskItem
import com.goalmaker.app.ui.lists.FilterChoices

data class FocusUiState(
    /** The board in the owner's order, narrowed by the filter. */
    val items: List<TaskItem> = emptyList(),
    /** How many tasks the whole board holds, so a filter that hides them all can say so. */
    val onBoard: Int = 0,
    val areas: List<AreaItem> = emptyList(),
    /** The area and tag filter the board and the add sheet share, and what it can be set to. */
    val choices: FilterChoices = FilterChoices(),
    /** What is typed in the add sheet's search box. */
    val query: String = "",
    /** The open tasks not on the board that match the search and the filter, for the add sheet. */
    val candidates: List<TaskItem> = emptyList(),
    val showAddSheet: Boolean = false,
)
