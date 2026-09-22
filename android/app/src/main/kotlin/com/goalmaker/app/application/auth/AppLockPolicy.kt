package com.goalmaker.app.application.auth

import java.time.Duration
import java.time.Instant

/**
 * How long the phone may be away from the app before the lock asks again (docs/sign-in.md). The
 * lock goes up as the app leaves the screen, so nothing is left on show behind it; this only
 * decides whether coming back costs a fingerprint or lets the owner straight in.
 *
 * This is the phone's own rule. The PC has no lock and its week (GoalMaker.Core's SignInPolicy) has
 * no phone half, so there is nothing here for both apps to share.
 */
object AppLockPolicy {
    /** A moment away is not away: a share sheet, a file picker, the screen going dark and back. */
    val GRACE: Duration = Duration.ofSeconds(30)

    /** When an app that left the screen at [leftAt] starts asking to be unlocked. */
    fun asksAt(leftAt: Instant): Instant = leftAt.plus(GRACE)

    /**
     * Whether an app that left the screen at [leftAt] has to be unlocked when it comes back at
     * [now]. A clock that went backwards while the app was away says nothing about how long it was
     * gone, so it asks: one fingerprint is the cheap way to be wrong.
     */
    fun mustAsk(leftAt: Instant, now: Instant): Boolean = now.isBefore(leftAt) || !now.isBefore(asksAt(leftAt))
}
