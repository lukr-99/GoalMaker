package com.goalmaker.app.application.auth

import java.time.Instant
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The lock in front of the signed-in app on this phone (docs/sign-in.md), held to
 * [AppLockPolicy]. Off unless the owner turns it on, and it never signs anybody out: the session
 * is kept and refreshed the whole time, the lock only decides who gets to see it.
 *
 * It goes up as the app leaves the screen rather than when it comes back, so the app is already
 * covered by the time anything else can look at it. Coming back inside the grace takes it down
 * again without asking.
 *
 * A cold start has no moment written down, so a lock that is on starts locked.
 */
class AppLock(private val enabled: () -> Boolean, private val now: () -> Instant) {

    private val state = MutableStateFlow(enabled())

    /** Whether the app is covered. Always false while the lock is off. */
    val locked: StateFlow<Boolean> = state.asStateFlow()

    // When the app last left the screen unlocked, and null once that has been answered for.
    private var leftAt: Instant? = null

    /** The app is leaving the screen. Nothing to remember when it is covered already. */
    fun leftTheScreen() {
        if (!enabled() || state.value) return
        leftAt = now()
        state.value = true
    }

    /** The app is back. Straight in when it was only away a moment, otherwise it keeps asking. */
    fun cameBack() {
        if (!enabled()) {
            clear()
            return
        }
        val away = leftAt ?: return
        leftAt = null
        if (!AppLockPolicy.mustAsk(away, now())) state.value = false
    }

    /** The owner proved who they are, or signed in with the emailed code. */
    fun unlocked() = clear()

    /**
     * The toggle moved. Turning it off opens the app at once; turning it on takes hold the next
     * time the app leaves the screen, so Settings does not lock itself behind the owner.
     */
    fun turned(on: Boolean) {
        if (!on) clear()
    }

    private fun clear() {
        leftAt = null
        state.value = false
    }
}
