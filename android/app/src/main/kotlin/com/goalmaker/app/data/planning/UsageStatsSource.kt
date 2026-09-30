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
 * [UsageSource] over [UsageStatsManager] (docs/tally.md). An activity coming to the front opens a
 * stretch for its app and going to the back closes it; the screen going off or the lock screen showing
 * closes every open one. Time on the home screen and in the system's bars is left out. Nothing read
 * here is kept.
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
        // Keyed by package and activity, since one activity can resume before another of the same app pauses.
        val open = LinkedHashMap<Pair<String, String?>, Long>()
        val seen = HashSet<Pair<String, String?>>()
        val stretches = mutableListOf<UsageInterval>()
        var stopped = false
        fun close(key: Pair<String, String?>, since: Long, at: Long) {
            val begin = since.coerceAtLeast(start)
            val finish = at.coerceAtMost(end)
            if (finish > begin) stretches += UsageInterval(key.first, Instant.ofEpochMilli(begin), Instant.ofEpochMilli(finish))
        }
        val event = UsageEvents.Event()
        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            val at = event.timeStamp
            when (event.eventType) {
                RESUMED -> {
                    val key = event.packageName to event.className
                    seen += key
                    open.putIfAbsent(key, at)
                }
                PAUSED -> {
                    val key = event.packageName to event.className
                    val since = open.remove(key)
                    // An app already in front when the window began shows only as it leaves.
                    when {
                        since != null -> close(key, since, at)
                        key !in seen && !stopped -> close(key, start, at)
                    }
                    seen += key
                }
                SCREEN_OFF, KEYGUARD_SHOWN, SHUTDOWN -> {
                    open.forEach { (key, since) -> close(key, since, at) }
                    open.clear()
                    stopped = true
                }
            }
        }
        open.forEach { (key, since) -> close(key, since, end) }
        // The home screen and the system's own bars aren't time spent in anything.
        val skipped = homePackages() + SYSTEM_UI
        return stretches.filter { it.app !in skipped }.sortedBy { it.start }
    }

    // Every installed launcher, since the owner may have more than one.
    private fun homePackages(): Set<String> {
        val home = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        val found = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.packageManager.queryIntentActivities(home, PackageManager.ResolveInfoFlags.of(PackageManager.MATCH_ALL.toLong()))
        } else {
            @Suppress("DEPRECATION")
            context.packageManager.queryIntentActivities(home, PackageManager.MATCH_ALL)
        }
        return found.mapNotNull { it.activityInfo?.packageName }.toSet()
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
