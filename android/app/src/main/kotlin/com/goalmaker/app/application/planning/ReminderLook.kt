package com.goalmaker.app.application.planning

import java.time.LocalDate

/**
 * What one look at the reminders found (docs/reminders.md): the task reminders to show, and the
 * planning day whose evening Plan tomorrow reminder to show, with the planning days whose weekly or
 * monthly review reminder to show, if they are due (docs/reviews.md).
 */
data class ReminderLook(
    val reminders: List<ScheduledReminder>,
    val planTomorrow: LocalDate? = null,
    val weeklyReview: LocalDate? = null,
    val monthlyReview: LocalDate? = null,
)
