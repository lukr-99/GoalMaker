package com.goalmaker.app.ui.lifegoals

/** A life goal achieved, dropped or deleted, which the place offers to undo for a moment. */
data class LifeGoalUndo(val kind: Kind, val title: String, val undo: () -> Unit) {
    enum class Kind { ACHIEVED, DROPPED, DELETED }
}
