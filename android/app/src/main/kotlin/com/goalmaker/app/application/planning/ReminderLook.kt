package com.goalmaker.app.application.planning

import java.time.LocalDate

/**
 * What one look at the reminders found (docs/reminders.md): the task reminders to show, and the
 * planning day whose evening Plan tomorrow reminder to show, if it is due.
 */
data class ReminderLook(
    val reminders: List<ScheduledReminder>,
    val planTomorrow: LocalDate? = null,
)
