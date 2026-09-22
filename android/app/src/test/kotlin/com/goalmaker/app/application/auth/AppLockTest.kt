package com.goalmaker.app.application.auth

import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The optional lock in front of the signed-in app on this phone (docs/sign-in.md). */
class AppLockTest {
    private val start: Instant = Instant.parse("2026-09-21T09:00:00Z")
    private var now = start
    private var on = true
    private val lock = AppLock(enabled = { on }, now = { now })

    private fun at(seconds: Long) {
        now = start.plusSeconds(seconds)
    }

    @Test
    fun `the grace is half a minute after the app leaves the screen`() {
        assertEquals(start.plusSeconds(30), AppLockPolicy.asksAt(start))
        assertFalse(AppLockPolicy.mustAsk(start, start.plusSeconds(29)))
        assertTrue(AppLockPolicy.mustAsk(start, start.plusSeconds(30)))
        assertTrue(AppLockPolicy.mustAsk(start, start.plusSeconds(3600)))
    }

    @Test
    fun `a clock that went backwards asks rather than guesses`() {
        assertTrue(AppLockPolicy.mustAsk(start, start.minusSeconds(1)))
    }

    @Test
    fun `a cold start with the lock on is locked`() {
        assertTrue(AppLock(enabled = { true }, now = { start }).locked.value)
    }

    @Test
    fun `a cold start with the lock off is not`() {
        assertFalse(AppLock(enabled = { false }, now = { start }).locked.value)
    }

    @Test
    fun `the lock goes up as the app leaves, not when it comes back`() {
        lock.unlocked()

        lock.leftTheScreen()

        assertTrue(lock.locked.value)
    }

    @Test
    fun `a moment away lets the owner straight back in`() {
        lock.unlocked()
        lock.leftTheScreen()

        at(29)
        lock.cameBack()

        assertFalse(lock.locked.value)
    }

    @Test
    fun `longer than that asks`() {
        lock.unlocked()
        lock.leftTheScreen()

        at(30)
        lock.cameBack()

        assertTrue(lock.locked.value)
    }

    @Test
    fun `coming back to a lock that was never answered keeps it up`() {
        // Locked, away for a moment, back again: the grace belongs to an app that left unlocked.
        assertTrue(lock.locked.value)
        lock.leftTheScreen()

        at(5)
        lock.cameBack()

        assertTrue(lock.locked.value)
    }

    @Test
    fun `the owner proving who they are takes it down`() {
        lock.unlocked()

        assertFalse(lock.locked.value)
    }

    @Test
    fun `a lock that is off never goes up`() {
        on = false
        lock.unlocked()

        lock.leftTheScreen()
        at(3600)
        lock.cameBack()

        assertFalse(lock.locked.value)
    }

    @Test
    fun `turning it off opens the app at once`() {
        assertTrue(lock.locked.value)

        on = false
        lock.turned(on = false)

        assertFalse(lock.locked.value)
    }

    @Test
    fun `turning it on waits for the app to leave the screen`() {
        on = false
        lock.unlocked()

        on = true
        lock.turned(on = true)

        assertFalse(lock.locked.value)
        lock.leftTheScreen()
        assertTrue(lock.locked.value)
    }
}
