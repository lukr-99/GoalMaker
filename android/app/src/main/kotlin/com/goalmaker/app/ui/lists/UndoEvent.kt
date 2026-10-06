package com.goalmaker.app.ui.lists

/** Something just happened to a task (or a habit) that a snackbar offers to take back. */
data class UndoEvent(
    val kind: Kind,
    val title: String,
    val undo: () -> Unit,
) {
    /**
     * Done, deleted, (on a project's board) taken out of the project or off the board, (on the calendar)
     * just added, or an amount habit filled to its target in one tap.
     */
    enum class Kind { DONE, DELETED, OUT_OF_PROJECT, ARCHIVED, ADDED, FILLED }
}
