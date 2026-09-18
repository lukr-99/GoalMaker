package com.goalmaker.app.application.planning

import com.goalmaker.app.domain.planning.QuietHours
import java.time.LocalDateTime

/**
 * Which reminders a device has to act on (docs/reminders.md). Only one alarm is armed at a time:
 * when it goes off the device shows everything [due] and arms the [next] one, so a phone that was
 * off still catches up and no alarm quota is spent on reminders far ahead.
 */
object ReminderSchedule {
    /** Every reminder that has a time, soonest first, ties settled by id so both devices agree. */
    fun resolve(
        reminders: List<ReminderItem>,
        tasks: Map<String, TaskItem>,
        quietHours: QuietHours,
    ): List<ScheduledReminder> = reminders
        .mapNotNull { reminder ->
            val task = tasks[reminder.taskId] ?: return@mapNotNull null
            val at = ReminderRules.fires(reminder, task, quietHours) ?: return@mapNotNull null
            ScheduledReminder(reminder.id, task.id, task.title, at, reminder.important)
        }
        .sortedWith(compareBy({ it.at }, { it.id }))

    /** What to show now: reminders whose time has come or passed while nothing was listening. */
    fun due(
        reminders: List<ReminderItem>,
        tasks: Map<String, TaskItem>,
        quietHours: QuietHours,
        now: LocalDateTime,
    ): List<ScheduledReminder> = resolve(reminders, tasks, quietHours).filter { !it.at.isAfter(now) }

    /** The reminder to arm the next alarm for, or null when nothing is waiting. */
    fun next(
        reminders: List<ReminderItem>,
        tasks: Map<String, TaskItem>,
        quietHours: QuietHours,
        now: LocalDateTime,
    ): ScheduledReminder? = resolve(reminders, tasks, quietHours).firstOrNull { it.at.isAfter(now) }
}
