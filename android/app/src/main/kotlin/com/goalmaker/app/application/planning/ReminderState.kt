package com.goalmaker.app.application.planning

/** Where a reminder stands (the server's reminders.state). */
enum class ReminderState {
    PENDING,
    SNOOZED,
    DISMISSED,
    DONE,
}
