package com.goalmaker.app.application.planning

/**
 * The board of a project (docs/projects.md, contracts/vectors/projects.json): where a new item lands,
 * how a column and the task's own state move together, and the order items sit in.
 */
object ProjectRules {
    const val BACKLOG = "backlog"
    const val TODO = "todo"
    const val DOING = "doing"
    const val DONE = "done"

    const val TASK = "task"
    const val IDEA = "idea"
    const val BUG = "bug"

    const val LOW = "low"
    const val NORMAL = "normal"
    const val HIGH = "high"
    const val URGENT = "urgent"

    const val ACTIVE = "active"
    const val PAUSED = "paused"
    const val PROJECT_DONE = "done"

    /** The four columns, left to right. */
    val COLUMNS = listOf(BACKLOG, TODO, DOING, DONE)

    /** The priorities, most important first. */
    val PRIORITIES = listOf(URGENT, HIGH, NORMAL, LOW)

    /** The column a new item of [itemType] lands in: an idea in the backlog, anything else in to do. */
    fun columnFor(itemType: String): String = if (itemType == IDEA) BACKLOG else TODO

    /** How important a priority is; an unknown one counts as normal. */
    fun rank(priority: String): Int = when (priority) {
        URGENT -> 3
        HIGH -> 2
        LOW -> 0
        else -> 1
    }

    /** What moving an item to [column] does to a task in [state]: done finishes it, anything else reopens it. */
    fun moved(column: String, state: TaskState): TaskState = when {
        state == TaskState.DROPPED -> state
        column == DONE -> TaskState.DONE
        state == TaskState.DONE -> TaskState.OPEN
        else -> state
    }

    /** What finishing or reopening a task does to the [column] it sits in. */
    fun finished(state: TaskState, column: String): String = when {
        state == TaskState.DONE -> DONE
        state == TaskState.OPEN && column == DONE -> TODO
        else -> column
    }

    /** One column's items in the order the board shows them. */
    fun order(items: List<TaskItem>): List<TaskItem> = items.sortedWith(
        compareByDescending<TaskItem> { rank(it.priority) }
            .thenBy { it.position }
            .thenBy { it.createdAt }
            .thenBy { it.id },
    )

    /** The four columns of a project with their items in order; a column with nothing in it stays. */
    fun board(items: List<TaskItem>): List<ProjectColumn> = COLUMNS.map { column ->
        ProjectColumn(column, order(items.filter { !it.deleted && it.boardColumn == column }))
    }
}
