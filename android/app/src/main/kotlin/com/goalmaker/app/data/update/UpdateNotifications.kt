package com.goalmaker.app.data.update

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.content.getSystemService
import com.goalmaker.app.MainActivity
import com.goalmaker.app.R
import com.goalmaker.app.application.update.UpdateNotifier

/**
 * "GoalMaker X is ready" on its own channel, App updates: low importance, no sound, so it waits in
 * the shade without interrupting. Tapping it opens Settings at the update; Install opens the
 * installer from there; Later puts the version off. It is only shown when the owner allows
 * notifications, which the app asks for once for reminders; it never asks again for this.
 */
class UpdateNotifications(private val context: Context) : UpdateNotifier {
    private val manager = NotificationManagerCompat.from(context)

    /** Creates the channel. Safe to call again; Android keeps the owner's own changes. */
    fun createChannel() {
        val system = context.getSystemService<NotificationManager>() ?: return
        system.createNotificationChannel(
            NotificationChannel(CHANNEL, context.getString(R.string.update_channel), NotificationManager.IMPORTANCE_LOW).apply {
                description = context.getString(R.string.update_channel_description)
                setSound(null, null)
                enableVibration(false)
            },
        )
    }

    override fun show(version: String): Boolean {
        if (!allowed()) return false
        val notification = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(context.getString(R.string.update_ready_title, version))
            .setContentText(context.getString(R.string.update_ready_text))
            .setCategory(NotificationCompat.CATEGORY_RECOMMENDATION)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setSilent(true)
            .setOnlyAlertOnce(true)
            .setAutoCancel(true)
            .setContentIntent(open(UpdateIntents.EXTRA_OPEN_UPDATE, REQUEST_OPEN))
            .addAction(0, context.getString(R.string.update_ready_install), open(UpdateIntents.EXTRA_INSTALL_UPDATE, REQUEST_INSTALL))
            .addAction(0, context.getString(R.string.update_ready_later), later(version))
            .build()
        return try {
            manager.notify(ID, notification)
            true
        } catch (_: SecurityException) {
            false
        }
    }

    override fun cancel() = manager.cancel(ID)

    // From Android 13 the permission is the owner's; before it, only the app's notification switch.
    private fun allowed(): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            return false
        }
        return manager.areNotificationsEnabled()
    }

    // Install goes through the activity, since only an app on screen may open the installer.
    private fun open(extra: String, request: Int): PendingIntent = PendingIntent.getActivity(
        context,
        request,
        Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            .putExtra(extra, true),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    private fun later(version: String): PendingIntent = PendingIntent.getBroadcast(
        context,
        REQUEST_LATER,
        Intent(context, UpdateActionReceiver::class.java)
            .setAction(UpdateIntents.ACTION_LATER)
            .putExtra(UpdateIntents.EXTRA_VERSION, version),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    private companion object {
        const val CHANNEL = "app_updates"
        const val ID = 4101
        const val REQUEST_OPEN = 4101
        const val REQUEST_INSTALL = 4102
        const val REQUEST_LATER = 4103
    }
}
