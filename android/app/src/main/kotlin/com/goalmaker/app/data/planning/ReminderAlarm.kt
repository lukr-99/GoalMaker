package com.goalmaker.app.data.planning

import android.content.Context
import android.content.Intent

/** The intents the reminder receiver answers to, and how to build them. */
object ReminderAlarm {
    const val ACTION_FIRE = "com.goalmaker.app.action.REMINDER_FIRE"
    const val ACTION_DONE = "com.goalmaker.app.action.REMINDER_DONE"
    const val ACTION_DISMISS = "com.goalmaker.app.action.REMINDER_DISMISS"
    const val ACTION_SNOOZE = "com.goalmaker.app.action.REMINDER_SNOOZE"

    /** Later on a reminder: show the same notification again with every snooze. */
    const val ACTION_LATER = "com.goalmaker.app.action.REMINDER_LATER"
    const val ACTION_SKIP_PLAN = "com.goalmaker.app.action.PLAN_TOMORROW_SKIP"
    const val ACTION_SKIP_REVIEW = "com.goalmaker.app.action.REVIEW_SKIP"

    /** A habit reminder's buttons: check in (or add one), and skip the period. */
    const val ACTION_HABIT_CHECK_IN = "com.goalmaker.app.action.HABIT_CHECK_IN"
    const val ACTION_HABIT_SKIP = "com.goalmaker.app.action.HABIT_SKIP"

    /** A habit reminder's Log: it opens the app on the habit's log dialog rather than reaching the receiver. */
    const val ACTION_HABIT_LOG = "com.goalmaker.app.action.HABIT_LOG"

    const val EXTRA_REMINDER_ID = "reminder_id"
    const val EXTRA_SNOOZE = "snooze"

    /** What Later needs to show the reminder again: its task's title and whether it is important. */
    const val EXTRA_TASK_TITLE = "task_title"
    const val EXTRA_IMPORTANT = "important"

    /** The planning day of a Plan tomorrow reminder, as an ISO date. */
    const val EXTRA_PLAN_DAY = "plan_day"

    /** Which review a notification is about: its ritual, its planning day, and the period it looks back on. */
    const val EXTRA_REVIEW_RITUAL = "review_ritual"
    const val EXTRA_REVIEW_DAY = "review_day"
    const val EXTRA_REVIEW_KIND = "review_kind"
    const val EXTRA_REVIEW_PERIOD = "review_period"

    /** Which habit reminder a button or a tap is about: the habit's id and the planning day, an ISO date. */
    const val EXTRA_HABIT_ID = "habit_id"
    const val EXTRA_HABIT_DAY = "habit_day"

    /** Set on the intent that opens the app from the Plan tomorrow reminder. */
    const val EXTRA_OPEN_PLAN = "open_plan"
    const val EXTRA_OPEN_WANTS = "open_wants"

    /** Set on the intent that opens the Habits place from a habit reminder; [EXTRA_LOG_HABIT] also opens its log dialog. */
    const val EXTRA_OPEN_HABITS = "open_habits"
    const val EXTRA_LOG_HABIT = "log_habit"

    /** The one alarm a device has armed, so arming again replaces it. */
    const val ALARM_REQUEST_CODE = 1

    fun intent(context: Context, action: String): Intent =
        Intent(context, ReminderReceiver::class.java).setAction(action)
}
