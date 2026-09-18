package com.goalmaker.app.application.planning

import com.goalmaker.app.domain.planning.QuietHours
import java.time.LocalDateTime

/**
 * What a device does with its reminders when it looks (docs/reminders.md, pinned by
 * contracts/vectors/reminders.json). Only one alarm is armed at a time: when it goes off the device
 * shows what arrived since its last look and arms the [next] one, so a device that was off catches
 * up, a reminder is shown once, and no alarm quota is spent on reminders far ahead.
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

    /** What to show now: the reminders that arrived after [since], the last look, up to [now]. */
    fun due(
        reminders: List<ReminderItem>,
        tasks: Map<String, TaskItem>,
        quietHours: QuietHours,
        since: LocalDateTime,
        now: LocalDateTime,
    ): List<ScheduledReminder> = resolve(reminders, tasks, quietHours).filter { it.at.isAfter(since) && !it.at.isAfter(now) }

    /** The reminder to arm the next alarm for, or null when nothing is waiting. */
    fun next(
        reminders: List<ReminderItem>,
        tasks: Map<String, TaskItem>,
        quietHours: QuietHours,
        now: LocalDateTime,
    ): ScheduledReminder? = resolve(reminders, tasks, quietHours).firstOrNull { it.at.isAfter(now) }

    /**
     * Which of the notifications on screen ([shown], by reminder id) have to go: their reminder was
     * handled, snoozed, moved later or deleted, or its task finished, here or on the other device.
     */
    fun stale(
        shown: Collection<String>,
        reminders: List<ReminderItem>,
        tasks: Map<String, TaskItem>,
        now: LocalDateTime,
    ): List<String> {
        val byId = reminders.associateBy(ReminderItem::id)
        return shown.filter { id ->
            val reminder = byId[id] ?: return@filter true
            val task = tasks[reminder.taskId] ?: return@filter true
            val due = ReminderRules.due(reminder, task)
            due == null || due.isAfter(now)
        }.sorted()
    }
}
