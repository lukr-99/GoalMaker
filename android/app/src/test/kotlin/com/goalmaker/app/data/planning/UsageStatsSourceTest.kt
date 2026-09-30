package com.goalmaker.app.data.planning

import android.app.AppOpsManager
import android.app.Application
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.ApplicationInfo
import android.content.pm.ResolveInfo
import android.content.Context
import android.os.Process
import androidx.core.content.edit
import com.goalmaker.app.application.planning.NewRows
import com.goalmaker.app.application.planning.TallyDefaults
import com.goalmaker.app.application.planning.TallyList
import com.goalmaker.app.application.planning.TallyTracker
import com.goalmaker.app.application.planning.UsageInterval
import com.goalmaker.app.data.replica.TestReplica
import com.goalmaker.app.data.settings.SharedPreferencesSettingsStore
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
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
    fun `an app's own activities make one stretch, which ends at its last pause`() {
        event(YOUTUBE, 0, UsageEvents.Event.ACTIVITY_RESUMED, "Home")
        event(YOUTUBE, 2, UsageEvents.Event.ACTIVITY_RESUMED, "Watch")
        event(YOUTUBE, 3, UsageEvents.Event.ACTIVITY_PAUSED, "Home")
        event(YOUTUBE, 8, UsageEvents.Event.ACTIVITY_PAUSED, "Watch")

        assertEquals(listOf(stretch(YOUTUBE, 0, 8)), source.foreground(at(0), at(60)))
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
    fun `the emulator's morning after a boot keeps YouTube behind its trampoline`() {
        home(LAUNCHER)
        home(SETTINGS, FALLBACK_HOME, priority = -1000)
        emulatorMorning(locked = false)

        assertEquals(emulatorStretches, source.foreground(time("04:00:00"), time("12:22:30")))
    }

    @Test
    fun `the same morning reads alike when the lock screen showed first`() {
        home(LAUNCHER)
        home(SETTINGS, FALLBACK_HOME, priority = -1000)
        emulatorMorning(locked = true)

        assertEquals(emulatorStretches, source.foreground(time("04:00:00"), time("12:22:30")))
    }

    @Test
    fun `the Settings app counts, only its fallback home screen is left out`() {
        home(LAUNCHER)
        home(SETTINGS, FALLBACK_HOME, priority = -1000)
        event(SETTINGS, 0, UsageEvents.Event.ACTIVITY_RESUMED, "FallbackHome")
        event(SETTINGS, 1, UsageEvents.Event.ACTIVITY_PAUSED, "FallbackHome")
        event(LAUNCHER, 1, UsageEvents.Event.ACTIVITY_RESUMED, "Home")
        event(LAUNCHER, 2, UsageEvents.Event.ACTIVITY_PAUSED, "Home")
        event(SETTINGS, 2, UsageEvents.Event.ACTIVITY_RESUMED, "Settings")
        event(SETTINGS, 9, UsageEvents.Event.ACTIVITY_PAUSED, "Settings")
        event(LAUNCHER, 9, UsageEvents.Event.ACTIVITY_RESUMED, "Home")

        assertEquals(listOf(stretch(SETTINGS, 2, 9)), source.foreground(at(0), at(60)))
    }

    @Test
    fun `the emulator's morning becomes a minute of video, two of chat and two of other`() {
        home(LAUNCHER)
        home(SETTINGS, FALLBACK_HOME, priority = -1000)
        emulatorMorning()
        val preferences = application.getSharedPreferences("usage-test", Context.MODE_PRIVATE)
        preferences.edit(commit = true) { clear() }
        val settings = SharedPreferencesSettingsStore(preferences)
        TestReplica().use { test ->
            val now = time("12:22:28")
            val tally = TallyList(test.replica, NewRows(test.catalog, { TestReplica.OWNER }, { now }), {}) { "phone" }
            val defaults = TallyDefaults.load(application.assets.open("tally-rules.json"))
            val tracker = TallyTracker(source, tally, defaults.rules, settings, { now }, { ZoneOffset.UTC })
            tracker.turn(true)
            settings.setTallyReadUntil(time("12:17:00"))

            tracker.track()

            val day = LocalDate.parse("2026-09-30")
            assertEquals(listOf("chat" to 2, "other" to 2, "video" to 1), tally.totals(day, day).map { it.category to it.minutes })
        }
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

    private fun stretch(app: String, from: String, to: String) = UsageInterval(app, time(from), time(to))

    private fun time(clock: String): Instant = Instant.parse("2026-09-30T${clock}Z")

    private fun home(app: String, activity: String = "$app.Home", priority: Int = 0) =
        shadowOf(application.packageManager).addResolveInfoForIntent(
            Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME),
            ResolveInfo().apply {
                this.priority = priority
                activityInfo = ActivityInfo().apply {
                    packageName = app
                    name = activity
                    applicationInfo = ApplicationInfo().apply { packageName = app }
                }
            },
        )

    // What the morning below was: the launcher and the phone starting up are no app at all.
    private val emulatorStretches get() = listOf(
        stretch(GOALMAKER, "12:17:08", "12:18:47"),
        stretch(YOUTUBE, "12:18:48", "12:19:21"),
        stretch(MESSAGING, "12:20:53", "12:22:28"),
        stretch(GOALMAKER, "12:22:28", "12:22:30"),
    )

    // The events `dumpsys usagestats` showed on an API 35 emulator just after a boot (M8-11 check).
    private fun emulatorMorning(locked: Boolean = true) {
        fun on(clock: String, type: Int, app: String, activity: String) = events.addEvent(
            ShadowUsageStatsManager.EventBuilder.buildEvent()
                .setPackage(app)
                .setClass(activity)
                .setTimeStamp(time(clock).toEpochMilli())
                .setEventType(type)
                .build(),
        )
        val resumed = UsageEvents.Event.ACTIVITY_RESUMED
        val paused = UsageEvents.Event.ACTIVITY_PAUSED
        val shell = "com.google.android.apps.youtube.app.application.Shell_HomeActivity"
        val watch = "com.google.android.apps.youtube.app.watchwhile.MainActivity"
        if (locked) on("12:16:40", UsageEvents.Event.KEYGUARD_SHOWN, "android", "")
        on("12:16:51", resumed, SETTINGS, FALLBACK_HOME)
        on("12:16:52", paused, SETTINGS, FALLBACK_HOME)
        on("12:16:52", resumed, LAUNCHER, "NexusLauncherActivity")
        on("12:17:08", paused, LAUNCHER, "NexusLauncherActivity")
        on("12:17:08", resumed, GOALMAKER, "MainActivity")
        on("12:18:16", paused, GOALMAKER, "MainActivity")
        on("12:18:16", resumed, GOALMAKER, "MainActivity")
        on("12:18:31", paused, GOALMAKER, "MainActivity")
        on("12:18:31", resumed, GOALMAKER, "MainActivity")
        on("12:18:47", paused, GOALMAKER, "MainActivity")
        on("12:18:48", resumed, YOUTUBE, shell)
        on("12:18:48", paused, YOUTUBE, watch)
        on("12:18:48", resumed, YOUTUBE, watch)
        on("12:18:49", paused, YOUTUBE, watch)
        on("12:18:49", resumed, YOUTUBE, watch)
        on("12:19:15", paused, YOUTUBE, watch)
        on("12:19:15", resumed, YOUTUBE, watch)
        on("12:19:21", resumed, LAUNCHER, "NexusLauncherActivity")
        on("12:19:21", paused, YOUTUBE, watch)
        on("12:20:53", paused, LAUNCHER, "NexusLauncherActivity")
        on("12:20:53", resumed, MESSAGING, "MainActivity")
        on("12:20:53", paused, MESSAGING, "MainActivity")
        on("12:20:53", resumed, MESSAGING, "MainActivity")
        on("12:22:28", paused, MESSAGING, "MainActivity")
        on("12:22:28", resumed, GOALMAKER, "MainActivity")
    }

    private fun at(minute: Long): Instant = START.plusSeconds(minute * 60)

    private companion object {
        val START: Instant = Instant.parse("2026-09-28T10:00:00Z")
        const val YOUTUBE = "com.google.android.youtube"
        const val WHATSAPP = "com.whatsapp"
        const val CHROME = "com.android.chrome"
        const val MAPS = "com.google.android.apps.maps"
        const val LAUNCHER = "com.google.android.apps.nexuslauncher"
        const val OTHER_LAUNCHER = "com.teslacoilsw.launcher"
        const val SETTINGS = "com.android.settings"
        const val FALLBACK_HOME = "com.android.settings.FallbackHome"
        const val GOALMAKER = "com.goalmaker.app.debug"
        const val MESSAGING = "com.google.android.apps.messaging"
    }
}
