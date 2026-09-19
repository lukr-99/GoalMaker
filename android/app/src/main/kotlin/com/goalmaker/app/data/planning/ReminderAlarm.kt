package com.goalmaker.app.data.planning

import android.content.Context
import android.content.Intent

/** The intents the reminder receiver answers to, and how to build them. */
object ReminderAlarm {
    const val ACTION_FIRE = "com.goalmaker.app.action.REMINDER_FIRE"
    const val ACTION_DONE = "com.goalmaker.app.action.REMINDER_DONE"
    const val ACTION_DISMISS = "com.goalmaker.app.action.REMINDER_DISMISS"
    const val ACTION_SNOOZE = "com.goalmaker.app.action.REMINDER_SNOOZE"
    const val ACTION_SKIP_PLAN = "com.goalmaker.app.action.PLAN_TOMORROW_SKIP"
    const val ACTION_SKIP_REVIEW = "com.goalmaker.app.action.REVIEW_SKIP"

    const val EXTRA_REMINDER_ID = "reminder_id"
    const val EXTRA_SNOOZE = "snooze"

    /** The planning day of a Plan tomorrow reminder, as an ISO date. */
    const val EXTRA_PLAN_DAY = "plan_day"

    /** Which review a notification is about: its ritual, its planning day, and the period it looks back on. */
    const val EXTRA_REVIEW_RITUAL = "review_ritual"
    const val EXTRA_REVIEW_DAY = "review_day"
    const val EXTRA_REVIEW_KIND = "review_kind"
    const val EXTRA_REVIEW_PERIOD = "review_period"

    /** Set on the intent that opens the app from the Plan tomorrow reminder. */
    const val EXTRA_OPEN_PLAN = "open_plan"

    /** The one alarm a device has armed, so arming again replaces it. */
    const val ALARM_REQUEST_CODE = 1

    fun intent(context: Context, action: String): Intent =
        Intent(context, ReminderReceiver::class.java).setAction(action)
}
