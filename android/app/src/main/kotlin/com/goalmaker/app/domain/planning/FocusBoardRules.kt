package com.goalmaker.app.domain.planning

import com.goalmaker.app.application.planning.TaskItem
import com.goalmaker.app.application.planning.TaskState

/**
 * The Focus Board: a manually curated, ordered list of tasks the owner wants to keep an eye on,
 * such as school deadlines or important upcoming events. A task is on the board when its
 * [TaskItem.focusPosition] is not null; dragging it up or down changes that value.
 */
object FocusBoardRules {
    /**
     * The tasks currently on the Focus Board, in the owner's order. Done and deleted tasks are
     * excluded so the board stays clean; they leave quietly once completed.
     */
    fun board(tasks: List<TaskItem>): List<TaskItem> =
        tasks
            .filter { !it.deleted && it.state == TaskState.OPEN && it.focusPosition != null }
            .sortedWith(compareBy({ it.focusPosition }, { it.createdAt }))

    /**
     * The position for a task added at the end: one more than the current highest, or 1.0 when
     * the board is empty.
     */
    fun appendPosition(tasks: List<TaskItem>): Double =
        (board(tasks).lastOrNull()?.focusPosition ?: 0.0) + 1.0

    /**
     * The position for a task moved between [before] and [after]: their midpoint, or one beyond
     * [before] when [after] is null (dropped at the end), or one before [after] when [before] is
     * null (dropped at the top).
     */
    fun between(before: TaskItem?, after: TaskItem?): Double = when {
        before == null && after == null -> 1.0
        before == null -> (after!!.focusPosition ?: 1.0) - 1.0
        after == null -> (before.focusPosition ?: 1.0) + 1.0
        else -> ((before.focusPosition ?: 0.0) + (after.focusPosition ?: 0.0)) / 2.0
    }
}
