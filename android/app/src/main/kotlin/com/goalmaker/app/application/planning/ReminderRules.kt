package com.goalmaker.app.application.planning

import com.goalmaker.app.domain.planning.QuietHours
import java.time.LocalDateTime

/** When a reminder fires (docs/reminders.md, contracts/vectors/reminders.json). */
object ReminderRules {
    /**
     * The reminder's own time, before quiet hours. Null when it never fires: either side is
     * deleted, the task is no longer open, it is already handled, or it has no time to fire at.
     */
    fun due(reminder: ReminderItem, task: TaskItem): LocalDateTime? {
        if (reminder.deleted || task.deleted || task.state != TaskState.OPEN) return null
        return when (reminder.state) {
            ReminderState.DISMISSED, ReminderState.DONE -> null
            ReminderState.SNOOZED -> reminder.snoozedUntil
            ReminderState.PENDING -> pending(reminder, task)
        }
    }

    /** When the reminder actually arrives, with quiet hours applied (important ones pass). */
    fun fires(reminder: ReminderItem, task: TaskItem, quietHours: QuietHours): LocalDateTime? =
        due(reminder, task)?.let { quietHours.release(it, reminder.important) }

    private fun pending(reminder: ReminderItem, task: TaskItem): LocalDateTime? {
        reminder.fireAt?.let { return it }
        val offset = reminder.offsetMinutes ?: return null
        val date = task.plannedDate ?: return null
        val time = task.plannedTime ?: return null
        return date.atTime(time).plusMinutes(offset.toLong())
    }
}
