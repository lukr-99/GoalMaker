package com.goalmaker.app.application.planning

import java.time.LocalDateTime

/** A reminder resolved to the local time it arrives, with what the notification needs to show. */
data class ScheduledReminder(
    val id: String,
    val taskId: String,
    val taskTitle: String,
    val at: LocalDateTime,
    val important: Boolean,
)
