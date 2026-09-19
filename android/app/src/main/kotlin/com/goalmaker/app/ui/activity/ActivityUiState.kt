package com.goalmaker.app.ui.activity

import com.goalmaker.app.application.activity.UndoOutcome

/**
 * The Activity screen: the latest changes, whether the server could be reached, and what the last
 * Undo came to, until the owner has seen it.
 */
data class ActivityUiState(
    val loaded: Boolean = false,
    val rows: List<ActivityRow> = emptyList(),
    val unavailable: Boolean = false,
    val busy: Boolean = false,
    val undone: UndoOutcome? = null,
)
