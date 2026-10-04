package com.goalmaker.app.application.planning

import com.goalmaker.app.domain.planning.QuietHours
import com.goalmaker.app.domain.planning.ReviewReminder
import com.goalmaker.app.domain.planning.RitualReminder
import com.goalmaker.app.domain.planning.Snooze
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import kotlinx.coroutines.flow.Flow

/**
 * Keeps the device's reminders in step with the replica (docs/reminders.md): says what to show now,
 * arms the alarm for the next one, says which notifications on screen went stale, and settles a
 * reminder the owner handled. [remindedUntil] is the device's last look, kept on the device so each
 * reminder is shown once. The evening Plan tomorrow reminder shares the one alarm: it rings at
 * [planTomorrowAt] unless [rituals] says the ritual already ran that planning day, and so do the
 * [habits] that remind, on the days they are still left (HabitReminder). Every method
 * blocks on disk, so callers run them off the main thread.
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
    private val rituals: RitualRunList? = null,
    private val planTomorrowAt: () -> LocalTime? = { null },
    private val weeklyReviewAt: () -> LocalTime? = { null },
    private val weeklyReviewWeekday: () -> Int = { ReviewReminder.DEFAULT_WEEKDAY },
    private val monthlyReviewAt: () -> LocalTime? = { null },
    private val wants: WantList? = null,
    private val wantsReadyAt: () -> LocalTime? = { null },
    private val habits: HabitList? = null,
    private val lifeGoals: LifeGoalList? = null,
    private val whyFrequency: () -> WhyFrequency = { WhyFrequency.OFF },
) {
    /**
     * What arrived since the last look, including anything missed while the device was off, with the
     * alarm armed for whatever comes next. A device that never looked starts from now.
     */
    fun catchUp(): ReminderLook {
        val at = now()
        val since = remindedUntil() ?: at
        val (all, byId) = read()
        val due = ReminderSchedule.due(all, byId, quietHours(), since, at)
        val planDay = rituals?.let { RitualReminder.due(planTomorrowAt(), dayStartHour(), ranPlanTomorrow(), since, at) }
        val weekly = reviewDue(RitualRunList.WEEKLY_REVIEW, weeklyReviewAt(), since, at)
        val monthly = reviewDue(RitualRunList.MONTHLY_REVIEW, monthlyReviewAt(), since, at)
        val ready = wants?.let { WantReminder.due(wantsReadyAt(), dayStartHour(), it.all(), since, at) }
        val dueHabits = habits?.read()?.let { data ->
            data.habits.mapNotNull { habit ->
                HabitReminder.due(habit, data.checkinsOf(habit.id), data.pausesOf(habit.id), dayStartHour(), since, at)?.let { DueHabit(habit, it) }
            }
        }.orEmpty()
        val why = lifeGoals?.let { list ->
            WhyReminder.due(whyFrequency(), quietHours(), since, at)?.let { moment ->
                WhyReminder.goal(whyFrequency(), moment.periodStart, list.all())?.let { WhyDue(moment.periodStart, it.id) }
            }
        }
        setRemindedUntil(at)
        arm(all, byId, at)
        return ReminderLook(due, planDay, weekly, monthly, ready, dueHabits, why)
    }

    /** Whether the why reminder naming [lifeGoalId] has to go: that life goal is gone or no longer open. */
    fun whyStale(lifeGoalId: String): Boolean =
        lifeGoals?.get(lifeGoalId)?.status != LifeGoalRules.OPEN

    /** Whether the reminder on screen for [habitId] on planning [day] has to go: the habit is no longer left, or the day moved on. */
    fun habitStale(habitId: String, day: LocalDate): Boolean {
        val data = habits?.read() ?: return true
        val habit = data.habits.firstOrNull { it.id == habitId } ?: return true
        return HabitReminder.stale(habit, day, data.checkinsOf(habitId), data.pausesOf(habitId), dayStartHour(), now())
    }

    /** Check in from a habit's notification: a check is met, a count gets one more (an amount opens the app). */
    fun checkInHabit(habitId: String, day: LocalDate) {
        habits?.checkIn(habitId, day)
        rearm()
    }

    /** Skip from a habit's notification: its period that holds [day] neither counts nor breaks the streak. */
    fun skipHabit(habitId: String, day: LocalDate) {
        habits?.skip(habitId, day)
        rearm()
    }

    /** Which of the notifications on screen ([shown], by reminder id) have to go (docs/reminders.md). */
    fun stale(shown: Collection<String>): List<String> {
        if (shown.isEmpty()) return emptyList()
        val (all, byId) = read()
        return ReminderSchedule.stale(shown, all, byId, now())
    }

    /** Whether the wants notification naming [shown] has to go: every want in it was decided, here or elsewhere. */
    fun wantsStale(shown: Collection<String>): Boolean = wants?.let { WantReminder.stale(shown, it.all()) } ?: true

    /** Whether the Plan tomorrow reminder on screen for [day] has to go: the ritual ran, or the day moved on. */
    fun planTomorrowStale(day: LocalDate): Boolean =
        RitualReminder.stale(day, dayStartHour(), ranPlanTomorrow(), now())

    /** The ritual ran to the end on planning [day], so its reminder stays quiet that day on every device. */
    fun finishPlanTomorrow(day: LocalDate) {
        rituals?.record(RitualRunList.PLAN_TOMORROW, day)
        rearm()
    }

    /** "Not today" on the Plan tomorrow reminder: quiet for the rest of planning [day], on every device. */
    fun skipPlanTomorrow(day: LocalDate) {
        rituals?.record(RitualRunList.PLAN_TOMORROW, day, skipped = true)
        rearm()
    }

    /** Whether the review reminder of [ritual] on screen for [day] has to go: the review ran, or the day moved on. */
    fun reviewStale(ritual: String, day: LocalDate): Boolean =
        ReviewReminder.stale(day, dayStartHour(), ran(ritual), now())

    /** The review was written or put off on planning [day], so its reminder stays quiet that day everywhere. */
    fun finishReview(ritual: String, day: LocalDate, skipped: Boolean = false) {
        rituals?.record(ritual, day, skipped = skipped)
        rearm()
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
        arm(all, byId, now())
    }

    private fun read(): Pair<List<ReminderItem>, Map<String, TaskItem>> =
        reminders.all() to tasks.all().associateBy(TaskItem::id)

    private fun ranPlanTomorrow(): Set<LocalDate> = ran(RitualRunList.PLAN_TOMORROW)

    private fun ran(ritual: String): Set<LocalDate> = rituals?.ran(ritual).orEmpty()

    private fun reviewDue(ritual: String, time: LocalTime?, since: LocalDateTime, at: LocalDateTime): LocalDate? =
        rituals?.let { ReviewReminder.due(kindOf(ritual), time, weeklyReviewWeekday(), dayStartHour(), ran(ritual), since, at) }

    private fun reviewNext(ritual: String, time: LocalTime?, at: LocalDateTime): LocalDateTime? =
        rituals?.let { ReviewReminder.next(kindOf(ritual), time, weeklyReviewWeekday(), dayStartHour(), ran(ritual), at) }

    private fun kindOf(ritual: String) = if (ritual == RitualRunList.MONTHLY_REVIEW) "monthly" else "weekly"

    // One alarm for whichever comes first: a task's reminder or the evening Plan tomorrow reminder.
    private fun arm(all: List<ReminderItem>, byId: Map<String, TaskItem>, at: LocalDateTime) {
        val task = ReminderSchedule.next(all, byId, quietHours(), at)?.at
        val ritual = rituals?.let { RitualReminder.next(planTomorrowAt(), dayStartHour(), ranPlanTomorrow(), at) }
        val weekly = reviewNext(RitualRunList.WEEKLY_REVIEW, weeklyReviewAt(), at)
        val monthly = reviewNext(RitualRunList.MONTHLY_REVIEW, monthlyReviewAt(), at)
        val ready = wants?.let { WantReminder.next(wantsReadyAt(), dayStartHour(), it.all(), at) }
        val habit = habits?.read()?.let { data ->
            data.habits.mapNotNull { HabitReminder.next(it, data.checkinsOf(it.id), data.pausesOf(it.id), dayStartHour(), at) }.minOrNull()
        }
        // The why reminder waits while there is no open life goal to show.
        val why = lifeGoals?.takeIf { LifeGoalRules.open(it.all()).isNotEmpty() }?.let { WhyReminder.next(whyFrequency(), quietHours(), at) }
        val next = listOfNotNull(task, ritual, weekly, monthly, ready, habit, why).minOrNull()
        if (next == null) scheduler.cancel() else scheduler.armAt(next)
    }
}
