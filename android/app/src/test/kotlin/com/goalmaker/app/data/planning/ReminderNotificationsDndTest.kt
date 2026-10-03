package com.goalmaker.app.data.planning

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.provider.Settings
import com.goalmaker.app.application.planning.DndBreakthrough
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * What the phone says about important reminders and Do Not Disturb, read from the important
 * reminders' channel, and the system page Settings opens to change it (docs/reminders.md).
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class ReminderNotificationsDndTest {
    private val context: Context = RuntimeEnvironment.getApplication()
    private val system = context.getSystemService(NotificationManager::class.java)
    private val notifications = ReminderNotifications(context)

    @Before
    fun setUp() {
        shadowOf(system).setNotificationsEnabled(true)
    }

    // What the owner's "Override Do Not Disturb" switch, or switching the channel off, leaves behind.
    private fun ownerSets(importance: Int = NotificationManager.IMPORTANCE_HIGH, bypass: Boolean) {
        system.createNotificationChannel(
            NotificationChannel(ReminderNotifications.CHANNEL_IMPORTANT, "Important reminders", importance).apply { setBypassDnd(bypass) },
        )
    }

    @Test
    fun `the important channel keeps its id and does not override Do Not Disturb by itself`() {
        notifications.createChannels()
        val channel = system.getNotificationChannel("reminders_important")

        assertEquals(NotificationManager.IMPORTANCE_HIGH, channel.importance)
        assertEquals(false, channel.canBypassDnd())
        assertEquals(DndBreakthrough.NOT_ALLOWED, notifications.importantThroughDnd())
    }

    @Test
    fun `the owner's override lets important reminders through, and making the channels again keeps it`() {
        ownerSets(bypass = true)
        notifications.createChannels()

        assertEquals(DndBreakthrough.ALLOWED, notifications.importantThroughDnd())
    }

    @Test
    fun `a channel switched off and notifications switched off are told apart`() {
        notifications.createChannels()
        ownerSets(importance = NotificationManager.IMPORTANCE_NONE, bypass = true)
        assertEquals(DndBreakthrough.CHANNEL_OFF, notifications.importantThroughDnd())

        shadowOf(system).setNotificationsEnabled(false)
        assertEquals(DndBreakthrough.NOTIFICATIONS_OFF, notifications.importantThroughDnd())
    }

    @Test
    fun `Settings opens the important channel's page, or the app's while notifications are off`() {
        val channelPage = notifications.dndSettings(DndBreakthrough.NOT_ALLOWED)
        assertEquals(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS, channelPage.action)
        assertEquals("reminders_important", channelPage.getStringExtra(Settings.EXTRA_CHANNEL_ID))
        assertEquals(context.packageName, channelPage.getStringExtra(Settings.EXTRA_APP_PACKAGE))

        val appPage = notifications.dndSettings(DndBreakthrough.NOTIFICATIONS_OFF)
        assertEquals(Settings.ACTION_APP_NOTIFICATION_SETTINGS, appPage.action)
        assertEquals(context.packageName, appPage.getStringExtra(Settings.EXTRA_APP_PACKAGE))
    }
}
