package com.goalmaker.app.data.planning

import android.app.AppOpsManager
import android.app.Application
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.ResolveInfo
import android.os.Process
import com.goalmaker.app.application.planning.UsageInterval
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowUsageStatsManager

/** How Android's usage events become stretches of time in front (docs/tally.md, M8-11). */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class UsageStatsSourceTest {
    private val application get() = RuntimeEnvironment.getApplication()
    private val events get() = shadowOf(application.getSystemService(UsageStatsManager::class.java))
    private val source get() = UsageStatsSource(application)

    @Before
    fun setUp() = access(AppOpsManager.MODE_ALLOWED)

    @Test
    fun `an app is in front from resumed to paused, and the screen going off or locking stops the clock`() {
        event(YOUTUBE, 0, UsageEvents.Event.ACTIVITY_RESUMED)
        event(YOUTUBE, 10, UsageEvents.Event.ACTIVITY_PAUSED)
        event(WHATSAPP, 10, UsageEvents.Event.ACTIVITY_RESUMED)
        event("android", 15, UsageEvents.Event.SCREEN_NON_INTERACTIVE)
        event(WHATSAPP, 16, UsageEvents.Event.ACTIVITY_PAUSED)
        event(CHROME, 20, UsageEvents.Event.ACTIVITY_RESUMED)
        event("android", 25, UsageEvents.Event.KEYGUARD_SHOWN)
        event(MAPS, 50, UsageEvents.Event.ACTIVITY_RESUMED)

        assertEquals(
            listOf(
                stretch(YOUTUBE, 0, 10),
                stretch(WHATSAPP, 10, 15),
                stretch(CHROME, 20, 25),
                // Still in front when the window ends.
                stretch(MAPS, 50, 60),
            ),
            source.foreground(at(0), at(60)),
        )
    }

    @Test
    fun `an app already in front when the window starts counts from its start`() {
        event(CHROME, -5, UsageEvents.Event.ACTIVITY_RESUMED)
        event(CHROME, 5, UsageEvents.Event.ACTIVITY_PAUSED)
        event("android", 6, UsageEvents.Event.SCREEN_NON_INTERACTIVE)
        // After the screen went off, a lone pause says nothing about how long the app was in front.
        event(MAPS, 30, UsageEvents.Event.ACTIVITY_PAUSED)

        assertEquals(listOf(stretch(CHROME, 0, 5)), source.foreground(at(0), at(60)))
    }

    @Test
    fun `two activities of one app overlap rather than cut each other short`() {
        event(YOUTUBE, 0, UsageEvents.Event.ACTIVITY_RESUMED, "Home")
        event(YOUTUBE, 2, UsageEvents.Event.ACTIVITY_RESUMED, "Watch")
        event(YOUTUBE, 3, UsageEvents.Event.ACTIVITY_PAUSED, "Home")
        event(YOUTUBE, 8, UsageEvents.Event.ACTIVITY_PAUSED, "Watch")

        assertEquals(listOf(stretch(YOUTUBE, 0, 3), stretch(YOUTUBE, 2, 8)), source.foreground(at(0), at(60)))
    }

    @Test
    fun `time on any home screen and in the system's bars is left out`() {
        listOf(LAUNCHER, OTHER_LAUNCHER).forEach { app ->
            shadowOf(application.packageManager).addResolveInfoForIntent(
                Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME),
                ResolveInfo().apply { activityInfo = ActivityInfo().apply { packageName = app; name = "$app.Home" } },
            )
        }
        event(LAUNCHER, 0, UsageEvents.Event.ACTIVITY_RESUMED)
        event(LAUNCHER, 5, UsageEvents.Event.ACTIVITY_PAUSED)
        event(YOUTUBE, 5, UsageEvents.Event.ACTIVITY_RESUMED)
        event(YOUTUBE, 20, UsageEvents.Event.ACTIVITY_PAUSED)
        event(OTHER_LAUNCHER, 20, UsageEvents.Event.ACTIVITY_RESUMED)
        event(OTHER_LAUNCHER, 25, UsageEvents.Event.ACTIVITY_PAUSED)
        event("com.android.systemui", 25, UsageEvents.Event.ACTIVITY_RESUMED)
        event("com.android.systemui", 27, UsageEvents.Event.ACTIVITY_PAUSED)

        assertEquals(listOf(stretch(YOUTUBE, 5, 20)), source.foreground(at(0), at(60)))
    }

    @Test
    fun `without usage access nothing is read`() {
        event(YOUTUBE, 0, UsageEvents.Event.ACTIVITY_RESUMED)
        assertTrue(source.granted())

        access(AppOpsManager.MODE_IGNORED)

        assertFalse(source.granted())
        assertNull(source.foreground(at(0), at(60)))
    }

    private fun access(mode: Int) = shadowOf(application.getSystemService(AppOpsManager::class.java))
        .setMode(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), application.packageName, mode)

    private fun event(app: String, minute: Long, type: Int, activity: String = "Main") = events.addEvent(
        ShadowUsageStatsManager.EventBuilder.buildEvent()
            .setPackage(app)
            .setClass("$app.$activity")
            .setTimeStamp(at(minute).toEpochMilli())
            .setEventType(type)
            .build(),
    )

    private fun stretch(app: String, from: Long, to: Long) = UsageInterval(app, at(from), at(to))

    private fun at(minute: Long): Instant = START.plusSeconds(minute * 60)

    private companion object {
        val START: Instant = Instant.parse("2026-09-28T10:00:00Z")
        const val YOUTUBE = "com.google.android.youtube"
        const val WHATSAPP = "com.whatsapp"
        const val CHROME = "com.android.chrome"
        const val MAPS = "com.google.android.apps.maps"
        const val LAUNCHER = "com.google.android.apps.nexuslauncher"
        const val OTHER_LAUNCHER = "com.teslacoilsw.launcher"
    }
}
