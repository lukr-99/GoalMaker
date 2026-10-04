package com.goalmaker.app.application.planning

import java.time.Instant

/**
 * The phone's own record of which app was in front (docs/tally.md): Android keeps about a week of it.
 * GoalMaker only reads it, and only once the owner has granted usage access.
 */
interface UsageSource {
    /** Whether the owner has granted GoalMaker usage access in the system's settings. */
    fun granted(): Boolean

    /**
     * The stretches apps were in front from [from] to [to], cut to that window. The clock stops when
     * the screen goes off or the lock screen shows. Null when usage access isn't granted.
     */
    fun foreground(from: Instant, to: Instant): List<UsageInterval>?

    /** The name an app shows under its icon, for the Tally place's apps; null when the phone doesn't know the package. */
    fun appName(app: String): String?
}
