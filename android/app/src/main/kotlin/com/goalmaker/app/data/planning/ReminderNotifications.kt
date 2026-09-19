package com.goalmaker.app.data.planning

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.getSystemService
import com.goalmaker.app.MainActivity
import com.goalmaker.app.R
import com.goalmaker.app.application.planning.ScheduledReminder
import com.goalmaker.app.domain.planning.Snooze
import java.time.LocalDate

/**
 * Shows and clears reminder notifications (docs/reminders.md). Ordinary and important reminders get
 * their own channel, so the owner can tune each; an important one keeps ringing until it is handled.
 * Each notification is tagged with its reminder id, which is how stale ones are found after a sync.
 * The evening Plan tomorrow reminder has a third channel and is tagged with its planning day.
 */
class ReminderNotifications(private val context: Context) {
    private val manager = NotificationManagerCompat.from(context)

    /** Creates both channels. Safe to call again; Android keeps the owner's own changes. */
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
    fun show(reminder: ScheduledReminder) {
        if (!manager.areNotificationsEnabled()) return
        val channel = if (reminder.important) CHANNEL_IMPORTANT else CHANNEL
        val notification = NotificationCompat.Builder(context, channel)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(reminder.taskTitle)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setOnlyAlertOnce(true)
            .setOngoing(reminder.important)
            .setContentIntent(openApp(reminder.id))
            .setDeleteIntent(action(reminder.id, ReminderAlarm.ACTION_DISMISS, null))
            .addAction(0, context.getString(R.string.reminder_done), action(reminder.id, ReminderAlarm.ACTION_DONE, null))
            .addAction(0, context.getString(R.string.reminder_snooze_ten_minutes), snooze(reminder.id, Snooze.TEN_MINUTES))
            .addAction(0, context.getString(R.string.reminder_snooze_tomorrow), snooze(reminder.id, Snooze.TOMORROW_MORNING))
            .build()
        // Important reminders ring alarm-style until the owner acts (spec, story 57).
        if (reminder.important) notification.flags = notification.flags or Notification.FLAG_INSISTENT
        notify(reminder.id, notification)
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

    private fun skipPlan(day: LocalDate): PendingIntent = PendingIntent.getBroadcast(
        context,
        (ReminderAlarm.ACTION_SKIP_PLAN + day).hashCode(),
        ReminderAlarm.intent(context, ReminderAlarm.ACTION_SKIP_PLAN).putExtra(ReminderAlarm.EXTRA_PLAN_DAY, day.toString()),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    private fun snooze(reminderId: String, option: Snooze): PendingIntent =
        action(reminderId, ReminderAlarm.ACTION_SNOOZE, option)

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

    private companion object {
        const val CHANNEL = "reminders"
        const val CHANNEL_IMPORTANT = "reminders_important"
        const val CHANNEL_PLAN = "plan_tomorrow"
        const val ID = 4001
        const val PLAN_ID = 4002
    }
}
