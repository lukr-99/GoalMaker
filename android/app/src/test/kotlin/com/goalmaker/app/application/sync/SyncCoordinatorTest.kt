package com.goalmaker.app.application.sync

import android.app.Application
import com.goalmaker.app.data.replica.TestReplica
import java.time.Instant
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class SyncCoordinatorTest {
    private lateinit var test: TestReplica
    private val replica get() = test.replica
    private val server = FakeServer()
    private val now = Instant.parse("2026-09-18T12:00:00Z")

    private fun TestScope.coordinator() = SyncCoordinator(
        engine = SyncEngine(test.catalog, replica, server) { now },
        replica = replica,
        scope = backgroundScope,
        io = UnconfinedTestDispatcher(testScheduler),
        now = { now },
        debounce = 2.seconds,
    )

    @Before
    fun setUp() {
        test = TestReplica()
    }

    @After
    fun tearDown() = test.close()

    @Test
    fun `a burst of requests becomes one sync`() = runTest {
        val coordinator = coordinator()
        replica.queue("tasks", test.newTask("a", "Run"))

        coordinator.request()
        coordinator.request()
        coordinator.request()
        advanceTimeBy(1.seconds)
        runCurrent()
        assertEquals(0, server.calls)
        advanceTimeBy(2.seconds)
        runCurrent()

        assertEquals(1, server.upserts)
        assertEquals(1 + test.catalog.tables.size, server.calls)
        assertEquals(SyncState.IDLE, coordinator.status.value.state)
        assertEquals(now, coordinator.status.value.lastSyncedAt)
    }

    @Test
    fun `offline is shown with the waiting changes`() = runTest {
        val coordinator = coordinator()
        replica.queue("tasks", test.newTask("a", "Run"))
        server.offline = true

        coordinator.syncNow()

        assertEquals(SyncState.OFFLINE, coordinator.status.value.state)
        assertEquals(1, coordinator.status.value.pendingChanges)
    }

    @Test
    fun `offline retries on its own and backs off`() = runTest {
        val coordinator = coordinator()
        replica.queue("tasks", test.newTask("a", "Run"))
        server.offline = true

        coordinator.syncNow()
        advanceTimeBy(SyncCoordinator.FIRST_RETRY - 1.seconds)
        runCurrent()
        assertEquals(1, server.calls)
        advanceTimeBy(2.seconds)
        runCurrent()
        assertEquals(2, server.calls)

        // The second retry waits twice as long; the server is back by then.
        server.offline = false
        advanceTimeBy(SyncCoordinator.FIRST_RETRY)
        runCurrent()
        assertEquals(2, server.calls)
        advanceTimeBy(SyncCoordinator.FIRST_RETRY)
        runCurrent()

        assertEquals(SyncState.IDLE, coordinator.status.value.state)
        assertEquals(0, coordinator.status.value.pendingChanges)
        assertEquals(1, server.rows("tasks").size)
    }

    @Test
    fun `cancelling stops the offline retry`() = runTest {
        val coordinator = coordinator()
        server.offline = true
        coordinator.syncNow()

        coordinator.cancelScheduled()
        server.offline = false
        advanceTimeBy(SyncCoordinator.LONGEST_RETRY)
        runCurrent()

        assertEquals(SyncState.OFFLINE, coordinator.status.value.state)
    }

    @Test
    fun `sign-out keeps unsynced changes unless told otherwise`() = runTest {
        val coordinator = coordinator()
        replica.queue("tasks", test.newTask("a", "Run"))
        server.offline = true

        assertFalse(coordinator.flushAndClear(discardUnsynced = false))
        assertEquals(1, replica.outbox().size)

        assertTrue(coordinator.flushAndClear(discardUnsynced = true))
        assertTrue(replica.all("tasks").isEmpty())
    }

    @Test
    fun `sign-out after a successful push clears everything`() = runTest {
        val coordinator = coordinator()
        replica.queue("tasks", test.newTask("a", "Run"))

        assertTrue(coordinator.flushAndClear(discardUnsynced = false))

        assertEquals(1, server.rows("tasks").size)
        assertTrue(replica.all("tasks").isEmpty())
    }
}
