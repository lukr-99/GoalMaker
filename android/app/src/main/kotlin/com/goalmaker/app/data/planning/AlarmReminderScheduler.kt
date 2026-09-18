package com.goalmaker.app.data.planning

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.os.Build
import androidx.core.content.getSystemService
import com.goalmaker.app.application.planning.ReminderScheduler
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * The device's one reminder alarm, through `AlarmManager` (docs/reminders.md). Exact while the
 * system allows it, so a reminder arrives at its minute; otherwise inexact, which is late rather
 * than never. The alarm wakes [ReminderReceiver], which shows what is due and arms the next one.
 */
class AlarmReminderScheduler(
    private val context: Context,
    private val zone: () -> ZoneId = ZoneId::systemDefault,
) : ReminderScheduler {
    private val alarms = context.getSystemService<AlarmManager>()

    override fun armAt(at: LocalDateTime) {
        val manager = alarms ?: return
        val millis = at.atZone(zone()).toInstant().toEpochMilli()
        if (exactAllowed(manager)) {
            manager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, millis, pendingIntent())
        } else {
            manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, millis, pendingIntent())
        }
    }

    override fun cancel() {
        alarms?.cancel(pendingIntent())
    }

    private fun exactAllowed(manager: AlarmManager): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S || manager.canScheduleExactAlarms()

    private fun pendingIntent(): PendingIntent = PendingIntent.getBroadcast(
        context,
        ReminderAlarm.ALARM_REQUEST_CODE,
        ReminderAlarm.intent(context, ReminderAlarm.ACTION_FIRE),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )
}
