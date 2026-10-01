package com.goalmaker.app.data.planning

import androidx.annotation.StringRes
import com.goalmaker.app.R
import com.goalmaker.app.domain.planning.Snooze

/**
 * The buttons on a reminder notification (docs/reminders.md). Android shows at most three, and a
 * notification needs Done and the three snoozes Windows offers, so it has two faces: first Done,
 * 10 min and Later; Later turns the same notification into the three snoozes.
 */
object ReminderButtons {
    /** One button: its label, the broadcast it sends, and the snooze it picks, if any. */
    data class Button(@param:StringRes val label: Int, val action: String, val snooze: Snooze? = null)

    /** What a reminder shows when it fires. */
    val first: List<Button> = listOf(
        Button(R.string.reminder_done, ReminderAlarm.ACTION_DONE),
        Button(R.string.reminder_snooze_ten_minutes, ReminderAlarm.ACTION_SNOOZE, Snooze.TEN_MINUTES),
        Button(R.string.reminder_later, ReminderAlarm.ACTION_LATER),
    )

    /** What it shows after Later: every snooze, in the order Windows lists them. */
    val snoozes: List<Button> = listOf(
        Button(R.string.reminder_snooze_ten_minutes, ReminderAlarm.ACTION_SNOOZE, Snooze.TEN_MINUTES),
        Button(R.string.reminder_snooze_one_hour, ReminderAlarm.ACTION_SNOOZE, Snooze.ONE_HOUR),
        Button(R.string.reminder_snooze_tomorrow, ReminderAlarm.ACTION_SNOOZE, Snooze.TOMORROW_MORNING),
    )

    /** The most buttons Android shows on one notification. */
    const val MAX = 3

    /** The snooze a button's intent carries. One it doesn't know falls back to ten minutes. */
    fun snoozeOf(name: String?): Snooze = Snooze.entries.firstOrNull { it.name == name } ?: Snooze.TEN_MINUTES
}
