package com.goalmaker.app.application.planning

/** Today's open tasks by section (docs/lists.md), each in its order. */
data class TodaySections(
    val priorities: List<TaskItem>,
    val scheduled: List<TaskItem>,
    val more: List<TaskItem>,
    val overdue: List<TaskItem>,
) {
    val isEmpty: Boolean get() = priorities.isEmpty() && scheduled.isEmpty() && more.isEmpty() && overdue.isEmpty()
}
