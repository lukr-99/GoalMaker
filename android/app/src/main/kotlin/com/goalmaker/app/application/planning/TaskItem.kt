package com.goalmaker.app.application.planning

import java.time.LocalDate
import java.time.LocalTime

/** A task as the lists show it. M2-06 adds deadlines, steps and tags to what lists show. */
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
)
