package com.goalmaker.app.ui.lists

/** Something just happened to a task that a snackbar offers to take back. */
data class UndoEvent(
    val kind: Kind,
    val title: String,
    val undo: () -> Unit,
) {
    /** Done, deleted, or (on a project's board) taken out of the project. */
    enum class Kind { DONE, DELETED, OUT_OF_PROJECT }
}
