package com.goalmaker.app.application.planning

import com.goalmaker.app.domain.planning.QuietHours
import com.goalmaker.app.domain.planning.Snooze
import java.time.LocalDateTime
import kotlinx.coroutines.flow.Flow

/**
 * Keeps the device's reminders in step with the replica (docs/reminders.md): says what to show now,
 * arms the alarm for the next one, says which notifications on screen went stale, and settles a
 * reminder the owner handled. [remindedUntil] is the device's last look, kept on the device so each
 * reminder is shown once. Every method blocks on disk, so callers run them off the main thread.
 */
class ReminderService(
    private val reminders: ReminderList,
    private val tasks: TaskList,
    private val scheduler: ReminderScheduler,
    private val quietHours: () -> QuietHours,
    private val dayStartHour: () -> Int,
    private val now: () -> LocalDateTime,
    private val remindedUntil: () -> LocalDateTime?,
    private val setRemindedUntil: (LocalDateTime) -> Unit,
) {
    /**
     * What arrived since the last look, including anything missed while the device was off, with the
     * alarm armed for whatever comes next. A device that never looked starts from now.
     */
    fun catchUp(): List<ScheduledReminder> {
        val at = now()
        val (all, byId) = read()
        val due = ReminderSchedule.due(all, byId, quietHours(), remindedUntil() ?: at, at)
        setRemindedUntil(at)
        arm(ReminderSchedule.next(all, byId, quietHours(), at))
        return due
    }

    /** Which of the notifications on screen ([shown], by reminder id) have to go (docs/reminders.md). */
    fun stale(shown: Collection<String>): List<String> {
        if (shown.isEmpty()) return emptyList()
        val (all, byId) = read()
        return ReminderSchedule.stale(shown, all, byId, now())
    }

    /** Done from a notification: the task is finished and the reminder never comes back. */
    fun done(reminderId: String) {
        reminders.all().firstOrNull { it.id == reminderId }?.let { tasks.setDone(it.taskId, true) }
        reminders.markDone(reminderId)
        rearm()
    }

    /** Swiped away: the reminder never comes back, on this device or the other one. */
    fun dismiss(reminderId: String) {
        reminders.dismiss(reminderId)
        rearm()
    }

    /** Comes back where [option] says (docs/reminders.md). */
    fun snooze(reminderId: String, option: Snooze) {
        reminders.snooze(reminderId, option.target(now(), dayStartHour()))
        rearm()
    }

    /** Every reminder, again after each change to the table. Collect it off the main thread. */
    fun watch(): Flow<List<ReminderItem>> = reminders.watchAll()

    /** The reminders on one task, for the screen that edits them. */
    fun on(taskId: String): List<ReminderItem> = reminders.forTask(taskId)

    /** A reminder at its own time, armed right away. */
    fun addAt(taskId: String, at: LocalDateTime, important: Boolean = false): ReminderItem? =
        reminders.addAt(taskId, at, important).also { rearm() }

    /** A reminder [minutes] before the task's planned time, armed right away. */
    fun addBefore(taskId: String, minutes: Int, important: Boolean = false): ReminderItem? =
        reminders.addBefore(taskId, minutes, important).also { rearm() }

    /** Takes a reminder away for good. */
    fun remove(reminderId: String) {
        reminders.delete(reminderId)
        rearm()
    }

    /** Arms the alarm for the next reminder, after a sync, a settings change or a reboot. */
    fun rearm() {
        val (all, byId) = read()
        arm(ReminderSchedule.next(all, byId, quietHours(), now()))
    }

    private fun read(): Pair<List<ReminderItem>, Map<String, TaskItem>> =
        reminders.all() to tasks.all().associateBy(TaskItem::id)

    private fun arm(next: ScheduledReminder?) {
        if (next == null) scheduler.cancel() else scheduler.armAt(next.at)
    }
}
