package com.goalmaker.app.application.planning

import java.time.LocalDate
import java.time.LocalTime

/**
 * A task as the lists, the detail view and the archive show it. [seriesId] groups a repeating task's
 * occurrences (docs/repeating.md); [completedAt] is the server timestamp of a done task; [goalId] is
 * the goal it serves (docs/goals.md).
 */
data class TaskItem(
    val id: String,
    val title: String,
    val state: TaskState,
    val topPriority: Boolean,
    val createdAt: String,
    val plannedDate: LocalDate? = null,
    val plannedTime: LocalTime? = null,
    val areaId: String? = null,
    val recurrence: String? = null,
    val deleted: Boolean = false,
    val seriesId: String? = null,
    val notes: String = "",
    val deadline: LocalDate? = null,
    val completedAt: String? = null,
    val goalId: String? = null,
    val movedCount: Int = 0,
) {
    /** The day it was finished, by the server's timestamp, or null while it is not done. */
    val completedDay: LocalDate? get() {
        val stamp = completedAt?.takeIf { state == TaskState.DONE && it.length >= 10 } ?: return null
        return runCatching { LocalDate.parse(stamp.take(10)) }.getOrNull()
    }
}
