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
            .sortedWith(compareBy({ it.focusPosition }, { it.createdAt }, { it.id }))

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

    /**
     * The position that puts [taskId] at [place] on the board, 1 being the top, with the task itself
     * left out first. A place past the end is the end, and one below 1 is the top
     * (contracts/vectors/focus.json).
     */
    fun placeAt(tasks: List<TaskItem>, taskId: String, place: Int): Double {
        val others = board(tasks).filter { it.id != taskId }
        val index = (place - 1).coerceIn(0, others.size)
        return between(others.getOrNull(index - 1), others.getOrNull(index))
    }

    /**
     * The tasks the add picker offers for [query]: open, not deleted and not on the board, with every
     * word of the query in the title or the notes, ignoring case and accents. By their date (the
     * deadline, else the planned day), earliest first and undated last, then by title, then by id.
     */
    fun candidates(tasks: List<TaskItem>, query: String): List<TaskItem> {
        val words = AllTasksRules.words(query)
        return tasks
            .filter { !it.deleted && it.state == TaskState.OPEN && it.focusPosition == null && AllTasksRules.matches(it, words) }
            .sortedWith(AllTasksRules.BY_DATE)
    }
}
