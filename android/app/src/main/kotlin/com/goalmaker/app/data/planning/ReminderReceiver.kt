package com.goalmaker.app.data.planning

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.goalmaker.app.GoalMakerApplication
import com.goalmaker.app.domain.planning.Snooze
import java.time.LocalDate
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Everything that has to reach the reminders while no screen is open (docs/reminders.md): the alarm
 * going off, a button on a notification, and the moments a device forgets its alarms (a reboot, a
 * changed clock or time zone, an app update). Each one ends with the next alarm armed again.
 */
class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val graph = (context.applicationContext as? GoalMakerApplication)?.graph ?: return
        val reminders = graph.reminders
        val notifications = graph.reminderNotifications
        val reminderId = intent.getStringExtra(ReminderAlarm.EXTRA_REMINDER_ID)
        val snooze = intent.getStringExtra(ReminderAlarm.EXTRA_SNOOZE)
            ?.let { name -> Snooze.entries.firstOrNull { it.name == name } }
        val planDay = intent.getStringExtra(ReminderAlarm.EXTRA_PLAN_DAY)
            ?.let { runCatching { LocalDate.parse(it) }.getOrNull() }

        val finish = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                when (intent.action) {
                    ReminderAlarm.ACTION_DONE -> reminderId?.let {
                        reminders.done(it)
                        notifications.clear(it)
                    }

                    ReminderAlarm.ACTION_DISMISS -> reminderId?.let {
                        reminders.dismiss(it)
                        notifications.clear(it)
                    }

                    ReminderAlarm.ACTION_SNOOZE -> reminderId?.let {
                        reminders.snooze(it, snooze ?: Snooze.TEN_MINUTES)
                        notifications.clear(it)
                    }

                    ReminderAlarm.ACTION_SKIP_REVIEW -> {
                        val ritual = intent.getStringExtra(ReminderAlarm.EXTRA_REVIEW_RITUAL)
                        val day = intent.getStringExtra(ReminderAlarm.EXTRA_REVIEW_DAY)
                            ?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
                        if (ritual != null && day != null) {
                            reminders.finishReview(ritual, day, skipped = true)
                            notifications.clearReview(ritual, day)
                        }
                    }

                    ReminderAlarm.ACTION_SKIP_PLAN -> planDay?.let {
                        reminders.skipPlanTomorrow(it)
                        notifications.clearPlanTomorrow(it)
                    }

                    // The alarm, a reboot, a changed clock: show what is due and arm what follows.
                    else -> {
                        val look = reminders.catchUp()
                        look.reminders.forEach(notifications::show)
                        look.planTomorrow?.let(notifications::showPlanTomorrow)
                    }
                }
            } finally {
                finish.finish()
            }
        }
    }
}
