package com.goalmaker.app.data.planning

import android.content.Context
import android.content.Intent

/** The intents the reminder receiver answers to, and how to build them. */
object ReminderAlarm {
    const val ACTION_FIRE = "com.goalmaker.app.action.REMINDER_FIRE"
    const val ACTION_DONE = "com.goalmaker.app.action.REMINDER_DONE"
    const val ACTION_DISMISS = "com.goalmaker.app.action.REMINDER_DISMISS"
    const val ACTION_SNOOZE = "com.goalmaker.app.action.REMINDER_SNOOZE"

    const val EXTRA_REMINDER_ID = "reminder_id"
    const val EXTRA_SNOOZE = "snooze"

    /** The one alarm a device has armed, so arming again replaces it. */
    const val ALARM_REQUEST_CODE = 1

    fun intent(context: Context, action: String): Intent =
        Intent(context, ReminderReceiver::class.java).setAction(action)
}
