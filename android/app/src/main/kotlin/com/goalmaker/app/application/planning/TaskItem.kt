package com.goalmaker.app.application.planning

/** A task as the lists show it. M2 adds the planning fields (days, deadlines, areas, tags). */
data class TaskItem(
    val id: String,
    val title: String,
    val state: TaskState,
    val topPriority: Boolean,
    val createdAt: String,
)
