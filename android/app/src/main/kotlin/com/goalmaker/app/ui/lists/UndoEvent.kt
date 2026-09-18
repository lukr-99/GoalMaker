package com.goalmaker.app.ui.lists

/** Something just happened to a task that a snackbar offers to take back. */
data class UndoEvent(
    val kind: Kind,
    val title: String,
    val undo: () -> Unit,
) {
    enum class Kind { DONE, DELETED }
}
