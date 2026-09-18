package com.goalmaker.app.ui.lists

import com.goalmaker.app.application.planning.AreaItem

/** What a task row needs besides the task itself, read once per change instead of per row. */
data class RowContext(
    val areas: List<AreaItem>,
    val tagNames: List<String>,
    val reminded: Set<String>,
)
