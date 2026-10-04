package com.goalmaker.app.data.planning

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.os.Bundle
import android.provider.Settings
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.getSystemService
import com.goalmaker.app.MainActivity
import com.goalmaker.app.R
import com.goalmaker.app.application.planning.DndBreakthrough
import com.goalmaker.app.application.planning.DueHabit
import com.goalmaker.app.application.planning.HabitCheckin
import com.goalmaker.app.application.planning.HabitItem
import com.goalmaker.app.application.planning.HabitRules
import com.goalmaker.app.application.planning.ScheduledReminder
import com.goalmaker.app.application.planning.WantsDue
import com.goalmaker.app.application.planning.TimeLeft
import com.goalmaker.app.application.planning.TimeLeftUnit
import com.goalmaker.app.application.planning.WhyDue
import com.goalmaker.app.domain.planning.Snooze
import java.text.NumberFormat
import java.time.LocalDate

/**
 * Shows and clears reminder notifications (docs/reminders.md). Ordinary and important reminders get
 * their own channel, so the owner can tune each; an important one keeps ringing until it is handled.
 * Each notification is tagged with its reminder id, which is how stale ones are found after a sync.
 * The evening Plan tomorrow reminder has a third channel and is tagged with its planning day, and the
 * habit reminders have their own "Habits" channel, each tagged with its habit and planning day.
 *
 * Do Not Disturb silences the important channel too until the owner lets it through: Android only
 * honours a channel's "Override Do Not Disturb" when the owner turns it on (an app may set it only
 * with Do Not Disturb access, which is far more than GoalMaker needs), so [importantThroughDnd]
 * says where it stands and [dndSettings] opens the page where it is changed. The channel id stays
 * the same, so turning it on there lasts.
 */
class ReminderNotifications(private val context: Context) {
    private val manager = NotificationManagerCompat.from(context)

    /** Creates every channel. Safe to call again; Android keeps the owner's own changes. */
    fun createChannels() {
        val system = context.getSystemService<NotificationManager>() ?: return
        system.createNotificationChannel(
            NotificationChannel(CHANNEL, context.getString(R.string.reminder_channel), NotificationManager.IMPORTANCE_HIGH).apply {
                description = context.getString(R.string.reminder_channel_description)
            },
        )
        system.createNotificationChannel(
            NotificationChannel(
                CHANNEL_IMPORTANT,
                context.getString(R.string.reminder_channel_important),
                NotificationManager.IMPORTANCE_HIGH,
            ).apply {
                description = context.getString(R.string.reminder_channel_important_description)
                enableVibration(true)
            },
        )
        system.createNotificationChannel(
            NotificationChannel(CHANNEL_PLAN, context.getString(R.string.plan_reminder_channel), NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = context.getString(R.string.plan_reminder_channel_description)
            },
        )
        system.createNotificationChannel(
            NotificationChannel(CHANNEL_REVIEW, context.getString(R.string.review_reminder_channel), NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = context.getString(R.string.review_reminder_channel_description)
            },
        )
        system.createNotificationChannel(
            NotificationChannel(CHANNEL_WANTS, context.getString(R.string.wants_ready_channel), NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = context.getString(R.string.wants_ready_channel_description)
            },
        )
        system.createNotificationChannel(
            NotificationChannel(CHANNEL_WHY, context.getString(R.string.why_reminder_channel), NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = context.getString(R.string.why_reminder_channel_description)
            },
        )
        system.createNotificationChannel(
            NotificationChannel(CHANNEL_HABITS, context.getString(R.string.habit_reminder_channel), NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = context.getString(R.string.habit_reminder_channel_description)
            },
        )
    }

    /**
     * Shows the reminder of a habit still left on its planning day (docs/reminders.md): its emoji and
     * name, where the day or the period stands by [checkins] (the habit's own), and the buttons
     * [HabitReminderButtons] picks for it. Tapping it opens the Habits place.
     */
    fun showHabit(due: DueHabit, checkins: List<HabitCheckin>) {
        if (!manager.areNotificationsEnabled()) return
        val habit = due.habit
        val builder = NotificationCompat.Builder(context, CHANNEL_HABITS)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(habit.emoji?.takeIf(String::isNotBlank)?.let { "$it ${habit.name}" } ?: habit.name)
            .setContentText(habitText(habit, due.day, checkins))
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setAutoCancel(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(openHabits(habit.id, due.day, log = false))
        HabitReminderButtons.of(habit).forEach { button ->
            val intent = if (button.action == ReminderAlarm.ACTION_HABIT_LOG) {
                openHabits(habit.id, due.day, log = true)
            } else {
                habitAction(habit.id, due.day, button.action)
            }
            builder.addAction(0, context.getString(button.label), intent)
        }
        try {
            manager.notify(habitTag(habit.id, due.day), HABIT_ID, builder.build())
        } catch (_: SecurityException) {
            // Notifications aren't allowed yet; the habit is still waiting on Today and in Habits.
        }
    }

    /** Takes the reminder of habit [habitId] for planning [day] away. */
    fun clearHabit(habitId: String, day: LocalDate) = manager.cancel(habitTag(habitId, day), HABIT_ID)

    /** The habit reminders on screen now, as the habit's id and the planning day they belong to. */
    fun shownHabits(): List<Pair<String, LocalDate>> = manager.activeNotifications
        .filter { it.id == HABIT_ID }
        .mapNotNull { shown ->
            val tag = shown.tag ?: return@mapNotNull null
            val day = runCatching { LocalDate.parse(tag.substringAfterLast('/')) }.getOrNull() ?: return@mapNotNull null
            tag.substringBeforeLast('/', "").takeIf(String::isNotEmpty)?.let { it to day }
        }

    // Where a habit still left stands: the days a weekly or monthly one has met, a count's or an
    // amount's value against its target, or simply that a check is still to do. A limit never reminds.
    private fun habitText(habit: HabitItem, day: LocalDate, checkins: List<HabitCheckin>): String {
        val start = HabitRules.periodStart(habit, day)
        val end = HabitRules.periodEnd(habit, start)
        val met = checkins.count { !it.day.isBefore(start) && !it.day.isAfter(end) && HabitRules.dayMet(habit, it) }
        val times = habit.times ?: 1
        val value = checkins.firstOrNull { it.day == day && !it.skipped && !it.failed }?.value ?: 0.0
        val number = NumberFormat.getNumberInstance(context.resources.configuration.locales[0]).apply { maximumFractionDigits = 2 }
        val target = number.format(habit.target ?: 0.0)
        val unit = habit.unit?.takeIf(String::isNotBlank)
        return when {
            habit.cadence == HabitRules.PER_WEEK -> context.resources.getQuantityString(R.plurals.habits_met_week, times, met, times)
            habit.cadence == HabitRules.PER_MONTH -> context.resources.getQuantityString(R.plurals.habits_met_month, times, met, times)
            habit.measure == HabitRules.CHECK -> context.getString(R.string.habit_reminder_left)
            unit != null -> context.getString(R.string.habits_value_unit, number.format(value), target, unit)
            else -> context.getString(R.string.habits_value, number.format(value), target)
        }
    }

    /**
     * Shows the reminder to write the weekly or monthly review of the period [periodStart] begins.
     * Tapping it or "Review" opens the review, on the letter when a Claude routine wrote one first, and
     * then the reminder says so ([letter]); "Not now" keeps it quiet for the rest of the day on every
     * device (docs/reviews.md, docs/letter.md).
     */
    fun showReview(ritual: String, day: LocalDate, kind: String, periodStart: LocalDate, letter: Boolean = false) {
        if (!manager.areNotificationsEnabled()) return
        val monthly = kind == "monthly"
        val notification = NotificationCompat.Builder(context, CHANNEL_REVIEW)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(context.getString(if (monthly) R.string.review_reminder_title_monthly else R.string.review_reminder_title_weekly))
            .setContentText(
                context.getString(
                    when {
                        !letter -> R.string.review_reminder_text
                        monthly -> R.string.review_reminder_letter_monthly
                        else -> R.string.review_reminder_letter_weekly
                    },
                ),
            )
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setAutoCancel(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(openReview(kind, periodStart))
            .addAction(0, context.getString(R.string.review_reminder_start), openReview(kind, periodStart))
            .addAction(0, context.getString(R.string.review_reminder_skip), skipReview(ritual, day))
            .build()
        try {
            manager.notify(tagOf(ritual, day), REVIEW_ID, notification)
        } catch (_: SecurityException) {
            // Notifications aren't allowed yet; the review is still one tap away in the app.
        }
    }

    /**
     * Shows the wants that became ready (docs/wants.md): one notification a day, naming them. Tapping it
     * opens the Wants place; deciding every want it names takes it down on every device.
     */
    fun showWants(due: WantsDue, titles: List<String>) {
        if (titles.isEmpty() || !manager.areNotificationsEnabled()) return
        val title = if (titles.size == 1) {
            context.getString(R.string.wants_ready_one, titles.first())
        } else {
            context.resources.getQuantityString(R.plurals.wants_ready_many, titles.size, titles.size)
        }
        val notification = NotificationCompat.Builder(context, CHANNEL_WANTS)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(if (titles.size == 1) context.getString(R.string.wants_ready_text) else titles.joinToString(", "))
            .setStyle(NotificationCompat.BigTextStyle().bigText(titles.joinToString("\n")))
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setAutoCancel(true)
            .setContentIntent(openWants())
            .addExtras(Bundle().apply { putString(EXTRA_WANT_IDS, due.wantIds.joinToString(",")) })
            .build()
        try {
            manager.notify(due.day.toString(), WANTS_ID, notification)
        } catch (_: SecurityException) {
            // Notifications aren't allowed yet; the wants are waiting in the app.
        }
    }

    /**
     * Shows the why reminder (docs/life-goals.md): one life goal's title, its why and its time left,
     * with its first [picture] large when this device has it. Tapping it opens the Life goals place on
     * that life goal; achieving, dropping or deleting the life goal takes it down.
     */
    fun showWhy(due: WhyDue, title: String, why: String, timeLeft: TimeLeft?, picture: ByteArray?) {
        if (!manager.areNotificationsEnabled()) return
        val bitmap = picture?.let { runCatching { BitmapFactory.decodeByteArray(it, 0, it.size) }.getOrNull() }
        val builder = NotificationCompat.Builder(context, CHANNEL_WHY)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(why)
            .setSubText(timeLeft?.let(::timeLeftText))
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setAutoCancel(true)
            .setContentIntent(openLifeGoal(due.lifeGoalId))
        builder.setStyle(
            if (bitmap != null) {
                NotificationCompat.BigPictureStyle().bigPicture(bitmap).setSummaryText(why)
            } else {
                NotificationCompat.BigTextStyle().bigText(why)
            },
        )
        try {
            manager.notify(due.lifeGoalId, WHY_ID, builder.build())
        } catch (_: SecurityException) {
            // Notifications aren't allowed yet; the life goal is still in the app.
        }
    }

    private fun timeLeftText(left: TimeLeft): String = when (left.unit) {
        TimeLeftUnit.YEARS -> context.resources.getQuantityString(R.plurals.life_goals_years_left, left.count, left.count)
        TimeLeftUnit.MONTHS -> context.resources.getQuantityString(R.plurals.life_goals_months_left, left.count, left.count)
        TimeLeftUnit.DAYS -> context.resources.getQuantityString(R.plurals.life_goals_days_left, left.count, left.count)
        TimeLeftUnit.TODAY -> context.getString(R.string.life_goals_today)
        TimeLeftUnit.PAST -> context.getString(R.string.life_goals_past)
    }

    /** The life goals whose why reminder is on screen. */
    fun shownWhy(): List<String> = manager.activeNotifications.filter { it.id == WHY_ID }.mapNotNull { it.tag }

    fun clearWhy(lifeGoalId: String) = manager.cancel(lifeGoalId, WHY_ID)

    /** The wants notifications on screen, as their planning day and the wants they name. */
    fun shownWants(): List<Pair<LocalDate, List<String>>> = manager.activeNotifications
        .filter { it.id == WANTS_ID }
        .mapNotNull { shown ->
            val day = shown.tag?.let { runCatching { LocalDate.parse(it) }.getOrNull() } ?: return@mapNotNull null
            day to shown.notification.extras.getString(EXTRA_WANT_IDS).orEmpty().split(',').filter(String::isNotBlank)
        }

    fun clearWants(day: LocalDate) = manager.cancel(day.toString(), WANTS_ID)

    /** Takes the review reminder of [ritual] for [day] away. */
    fun clearReview(ritual: String, day: LocalDate) = manager.cancel(tagOf(ritual, day), REVIEW_ID)

    /** The review reminders on screen now, as the ritual and the planning day they belong to. */
    fun shownReviews(): List<Pair<String, LocalDate>> = manager.activeNotifications
        .filter { it.id == REVIEW_ID }
        .mapNotNull { notification ->
            notification.tag?.split('/')?.takeIf { it.size == 2 }?.let { (ritual, day) ->
                runCatching { ritual to LocalDate.parse(day) }.getOrNull()
            }
        }

    /**
     * Shows the evening reminder to plan tomorrow, for planning [day]. Tapping it or "Plan" opens the
     * ritual; "Not today" keeps it quiet for the rest of the day on every device.
     */
    fun showPlanTomorrow(day: LocalDate) {
        if (!manager.areNotificationsEnabled()) return
        val tag = day.toString()
        val notification = NotificationCompat.Builder(context, CHANNEL_PLAN)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(context.getString(R.string.plan_reminder_title))
            .setContentText(context.getString(R.string.plan_reminder_text))
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setAutoCancel(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(openPlan())
            .addAction(0, context.getString(R.string.plan_reminder_start), openPlan())
            .addAction(0, context.getString(R.string.plan_reminder_skip), skipPlan(day))
            .build()
        try {
            manager.notify(tag, PLAN_ID, notification)
        } catch (_: SecurityException) {
            // Notifications aren't allowed yet; the ritual is still one tap away in the app.
        }
    }

    /** Takes the Plan tomorrow reminder for [day] away. */
    fun clearPlanTomorrow(day: LocalDate) = manager.cancel(day.toString(), PLAN_ID)

    /** The planning days whose Plan tomorrow reminder is on screen now. */
    fun shownPlanTomorrow(): List<LocalDate> = manager.activeNotifications
        .filter { it.id == PLAN_ID }
        .mapNotNull { notification -> notification.tag?.let { runCatching { LocalDate.parse(it) }.getOrNull() } }

    /** Shows [reminder]. Does nothing when notifications are switched off, which is the owner's call. */
    fun show(reminder: ScheduledReminder) = post(reminder.id, reminder.taskTitle, reminder.important, ReminderButtons.first)

    /**
     * Later: shows the reminder on screen again in place, quietly, with every snooze (docs/reminders.md).
     * Does nothing when it is no longer on screen, so a reminder handled meanwhile doesn't come back.
     */
    fun showSnoozes(reminderId: String, taskTitle: String, important: Boolean) {
        if (reminderId !in shown()) return
        post(reminderId, taskTitle, important, ReminderButtons.snoozes)
    }

    private fun post(reminderId: String, taskTitle: String, important: Boolean, buttons: List<ReminderButtons.Button>) {
        if (!manager.areNotificationsEnabled()) return
        val channel = if (important) CHANNEL_IMPORTANT else CHANNEL
        val builder = NotificationCompat.Builder(context, channel)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(taskTitle)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setOnlyAlertOnce(true)
            .setOngoing(important)
            .setContentIntent(openApp(reminderId))
            .setDeleteIntent(action(reminderId, ReminderAlarm.ACTION_DISMISS, null))
        buttons.forEach { button ->
            val intent = if (button.action == ReminderAlarm.ACTION_LATER) {
                later(reminderId, taskTitle, important)
            } else {
                action(reminderId, button.action, button.snooze)
            }
            builder.addAction(0, context.getString(button.label), intent)
        }
        val notification = builder.build()
        // Important reminders ring alarm-style until the owner acts (spec, story 57).
        if (important) notification.flags = notification.flags or Notification.FLAG_INSISTENT
        notify(reminderId, notification)
    }

    /** Whether an important reminder rings through Do Not Disturb on this phone (docs/reminders.md). */
    fun importantThroughDnd(): DndBreakthrough {
        val channel = context.getSystemService<NotificationManager>()?.getNotificationChannel(CHANNEL_IMPORTANT)
        return DndBreakthrough.of(
            notificationsOn = manager.areNotificationsEnabled(),
            channelOn = channel == null || channel.importance != NotificationManager.IMPORTANCE_NONE,
            bypassesDnd = channel?.canBypassDnd() == true,
        )
    }

    /**
     * The system page that changes [state]: the important reminders' channel, with its "Override Do
     * Not Disturb" switch, or the app's notification page while notifications are off altogether.
     */
    fun dndSettings(state: DndBreakthrough): Intent {
        createChannels()
        val page = if (state.opensAppPage) {
            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
        } else {
            Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_CHANNEL_ID, CHANNEL_IMPORTANT)
        }
        return page.putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
    }

    /** Takes a reminder's notification away, because it was handled here or on the other device. */
    fun clear(reminderId: String) = manager.cancel(reminderId, ID)

    /** The reminder ids whose notifications are on screen now. */
    fun shown(): List<String> = manager.activeNotifications.filter { it.id == ID }.mapNotNull { it.tag }

    private fun notify(reminderId: String, notification: Notification) {
        try {
            manager.notify(reminderId, ID, notification)
        } catch (_: SecurityException) {
            // The owner has not granted notifications yet; the reminder still settles in the replica.
        }
    }

    // Opening the app from a notification counts as dismissing it (docs/reminders.md). The activity
    // settles it, because Android no longer lets a notification tap go through a receiver first.
    private fun openApp(reminderId: String): PendingIntent = PendingIntent.getActivity(
        context,
        reminderId.hashCode(),
        Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            .putExtra(ReminderAlarm.EXTRA_REMINDER_ID, reminderId),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    private fun openPlan(): PendingIntent = PendingIntent.getActivity(
        context,
        PLAN_ID,
        Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            .putExtra(ReminderAlarm.EXTRA_OPEN_PLAN, true),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    private fun openWants(): PendingIntent = PendingIntent.getActivity(
        context,
        WANTS_ID,
        Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            .putExtra(ReminderAlarm.EXTRA_OPEN_WANTS, true),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    private fun openLifeGoal(lifeGoalId: String): PendingIntent = PendingIntent.getActivity(
        context,
        (WHY_ID.toString() + lifeGoalId).hashCode(),
        Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            .putExtra(ReminderAlarm.EXTRA_OPEN_LIFE_GOAL, lifeGoalId),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    private fun openReview(kind: String, periodStart: LocalDate): PendingIntent = PendingIntent.getActivity(
        context,
        (REVIEW_ID.toString() + kind + periodStart).hashCode(),
        Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            .putExtra(ReminderAlarm.EXTRA_REVIEW_KIND, kind)
            .putExtra(ReminderAlarm.EXTRA_REVIEW_PERIOD, periodStart.toString()),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    private fun skipReview(ritual: String, day: LocalDate): PendingIntent = PendingIntent.getBroadcast(
        context,
        (ReminderAlarm.ACTION_SKIP_REVIEW + ritual + day).hashCode(),
        ReminderAlarm.intent(context, ReminderAlarm.ACTION_SKIP_REVIEW)
            .putExtra(ReminderAlarm.EXTRA_REVIEW_RITUAL, ritual)
            .putExtra(ReminderAlarm.EXTRA_REVIEW_DAY, day.toString()),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    private fun tagOf(ritual: String, day: LocalDate) = "$ritual/$day"

    private fun habitTag(habitId: String, day: LocalDate) = "$habitId/$day"

    // The body opens the Habits place; Log opens the habit's log dialog there too.
    private fun openHabits(habitId: String, day: LocalDate, log: Boolean): PendingIntent = PendingIntent.getActivity(
        context,
        (habitTag(habitId, day) + if (log) ReminderAlarm.ACTION_HABIT_LOG else ReminderAlarm.EXTRA_OPEN_HABITS).hashCode(),
        Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            .putExtra(ReminderAlarm.EXTRA_OPEN_HABITS, true)
            .putExtra(ReminderAlarm.EXTRA_HABIT_ID, habitId)
            .putExtra(ReminderAlarm.EXTRA_HABIT_DAY, day.toString())
            .putExtra(ReminderAlarm.EXTRA_LOG_HABIT, log),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    private fun habitAction(habitId: String, day: LocalDate, action: String): PendingIntent = PendingIntent.getBroadcast(
        context,
        (habitTag(habitId, day) + action).hashCode(),
        ReminderAlarm.intent(context, action)
            .putExtra(ReminderAlarm.EXTRA_HABIT_ID, habitId)
            .putExtra(ReminderAlarm.EXTRA_HABIT_DAY, day.toString()),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    private fun skipPlan(day: LocalDate): PendingIntent = PendingIntent.getBroadcast(
        context,
        (ReminderAlarm.ACTION_SKIP_PLAN + day).hashCode(),
        ReminderAlarm.intent(context, ReminderAlarm.ACTION_SKIP_PLAN).putExtra(ReminderAlarm.EXTRA_PLAN_DAY, day.toString()),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    private fun later(reminderId: String, taskTitle: String, important: Boolean): PendingIntent = PendingIntent.getBroadcast(
        context,
        (reminderId + ReminderAlarm.ACTION_LATER).hashCode(),
        ReminderAlarm.intent(context, ReminderAlarm.ACTION_LATER)
            .putExtra(ReminderAlarm.EXTRA_REMINDER_ID, reminderId)
            .putExtra(ReminderAlarm.EXTRA_TASK_TITLE, taskTitle)
            .putExtra(ReminderAlarm.EXTRA_IMPORTANT, important),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    // One request code per reminder and action, so notifications never share a pending intent.
    private fun action(reminderId: String, action: String, option: Snooze?): PendingIntent {
        val intent = ReminderAlarm.intent(context, action)
            .putExtra(ReminderAlarm.EXTRA_REMINDER_ID, reminderId)
            .apply { option?.let { putExtra(ReminderAlarm.EXTRA_SNOOZE, it.name) } }
        val code = (reminderId + action + option?.name).hashCode()
        return PendingIntent.getBroadcast(
            context,
            code,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    companion object {
        private const val CHANNEL = "reminders"

        /** The important reminders' channel; its id never changes, so the owner's DND choice on it lasts. */
        const val CHANNEL_IMPORTANT = "reminders_important"
        private const val CHANNEL_PLAN = "plan_tomorrow"
        private const val CHANNEL_REVIEW = "reviews"
        private const val ID = 4001
        private const val PLAN_ID = 4002
        private const val REVIEW_ID = 4003
        private const val WANTS_ID = 4004
        private const val CHANNEL_WANTS = "wants_ready"

        /** The habit reminders' channel, so the owner can silence them apart from task reminders. */
        const val CHANNEL_HABITS = "habits"
        private const val HABIT_ID = 4005
        private const val EXTRA_WANT_IDS = "com.goalmaker.app.WANT_IDS"

        /** The why reminder's channel, so the owner can silence it apart from the rest. */
        private const val CHANNEL_WHY = "why_reminder"
        private const val WHY_ID = 4006
    }
}
