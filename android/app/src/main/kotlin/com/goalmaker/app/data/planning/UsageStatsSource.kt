package com.goalmaker.app.data.planning

import android.Manifest
import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Process
import com.goalmaker.app.application.planning.UsageInterval
import com.goalmaker.app.application.planning.UsageSource
import java.time.Instant

/**
 * [UsageSource] over [UsageStatsManager] (docs/tally.md). An app is in front from the moment one of
 * its activities resumes until another app resumes, the screen goes off or the lock screen shows.
 * Time on the home screen and in the system's bars is left out. Nothing read here is kept.
 */
class UsageStatsSource(private val context: Context) : UsageSource {

    override fun granted(): Boolean {
        val appOps = context.getSystemService(AppOpsManager::class.java) ?: return false
        val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            appOps.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName)
        } else {
            @Suppress("DEPRECATION")
            appOps.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName)
        }
        // Some phones leave the default in place and let the permission decide.
        return if (mode == AppOpsManager.MODE_DEFAULT) {
            context.checkCallingOrSelfPermission(Manifest.permission.PACKAGE_USAGE_STATS) == PackageManager.PERMISSION_GRANTED
        } else {
            mode == AppOpsManager.MODE_ALLOWED
        }
    }

    override fun foreground(from: Instant, to: Instant): List<UsageInterval>? {
        if (!granted()) return null
        if (!to.isAfter(from)) return emptyList()
        val manager = context.getSystemService(UsageStatsManager::class.java) ?: return null
        // Access taken away between the check and the read.
        val events = try {
            manager.queryEvents(from.toEpochMilli(), to.toEpochMilli())
        } catch (_: SecurityException) {
            return null
        } ?: return emptyList()
        val start = from.toEpochMilli()
        val end = to.toEpochMilli()
        val home = homeScreens()
        val stretches = mutableListOf<UsageInterval>()
        // One app is in front at a time: from its first resume until another app resumes or the clock
        // stops. Activities inside an app come and go (a trampoline may resume and never pause), so
        // only the package counts. A pause ends the app's time only if nothing of it resumes again.
        var app: String? = null
        var since = 0L
        var pausedAt: Long? = null
        var anything = false
        fun leave(at: Long) {
            val current = app ?: return
            val begin = since.coerceAtLeast(start)
            val finish = (pausedAt ?: at).coerceAtMost(end)
            if (finish > begin) stretches += UsageInterval(current, Instant.ofEpochMilli(begin), Instant.ofEpochMilli(finish))
            app = null
            pausedAt = null
        }
        val event = UsageEvents.Event()
        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            val at = event.timeStamp
            val name = event.packageName
            when (event.eventType) {
                RESUMED -> when {
                    home.skips(name, event.className) -> leave(at)
                    name == app -> pausedAt = null
                    else -> {
                        leave(at)
                        app = name
                        since = at
                    }
                }
                PAUSED -> when {
                    name == app -> pausedAt = at
                    // Only a pause before anything else says the app was in front when the window began,
                    // which is the start of a planning day; later, a pause without a resume is noise.
                    !anything && !home.skips(name, event.className) -> {
                        app = name
                        since = start
                        pausedAt = at
                    }
                }
                SCREEN_OFF, KEYGUARD_SHOWN, SHUTDOWN -> leave(at)
                else -> continue
            }
            anything = true
        }
        leave(end)
        return stretches
    }

    /**
     * What counts as no app at all: every installed launcher, the system's bars, and home activities
     * that aren't a launcher, such as the Settings app's FallbackHome, which shows while the phone
     * starts (its negative priority says it is only a fallback, and the rest of Settings still counts).
     */
    private fun homeScreens(): HomeScreens {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        val found = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.packageManager.queryIntentActivities(intent, PackageManager.ResolveInfoFlags.of(PackageManager.MATCH_ALL.toLong()))
        } else {
            @Suppress("DEPRECATION")
            context.packageManager.queryIntentActivities(intent, PackageManager.MATCH_ALL)
        }
        return HomeScreens(
            packages = found.filter { it.priority >= 0 }.mapNotNull { it.activityInfo?.packageName }.toSet() + SYSTEM_UI,
            activities = found.mapNotNull { it.activityInfo }.map { it.packageName to it.name }.toSet(),
        )
    }

    private class HomeScreens(val packages: Set<String>, val activities: Set<Pair<String, String?>>) {
        fun skips(app: String, activity: String?): Boolean = app in packages || (app to activity) in activities
    }

    private companion object {
        // UsageEvents.Event's values, named here since the older names are deprecated and the newer
        // ones need a later API, while the numbers are the same on every version.
        const val RESUMED = 1 // ACTIVITY_RESUMED, MOVE_TO_FOREGROUND before Android 10
        const val PAUSED = 2 // ACTIVITY_PAUSED, MOVE_TO_BACKGROUND before Android 10
        const val SCREEN_OFF = 16 // SCREEN_NON_INTERACTIVE
        const val KEYGUARD_SHOWN = 17
        const val SHUTDOWN = 26 // DEVICE_SHUTDOWN
        const val SYSTEM_UI = "com.android.systemui"
    }
}
