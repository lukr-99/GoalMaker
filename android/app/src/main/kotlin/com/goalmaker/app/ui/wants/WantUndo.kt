package com.goalmaker.app.ui.wants

/** A decision or a deletion the Wants place offers to undo for a moment. */
data class WantUndo(val kind: Kind, val title: String, val undo: () -> Unit) {
    enum class Kind { BOUGHT, DROPPED, DELETED }
}
