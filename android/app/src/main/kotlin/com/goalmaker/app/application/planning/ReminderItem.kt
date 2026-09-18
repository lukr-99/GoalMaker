package com.goalmaker.app.application.planning

import java.time.LocalDateTime

/**
 * A reminder as the device schedules it (docs/reminders.md). Either [fireAt] holds its own time or
 * [offsetMinutes] counts back from its task's planned time; both are local, so the adapter turns
 * the stored instants into the device's zone first.
 */
data class ReminderItem(
    val id: String,
    val taskId: String,
    val state: ReminderState,
    val important: Boolean = false,
    val fireAt: LocalDateTime? = null,
    val offsetMinutes: Int? = null,
    val snoozedUntil: LocalDateTime? = null,
    val deleted: Boolean = false,
)
