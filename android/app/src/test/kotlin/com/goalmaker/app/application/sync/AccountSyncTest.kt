package com.goalmaker.app.application.sync

import android.app.Application
import com.goalmaker.app.application.auth.AuthSession
import com.goalmaker.app.data.replica.TestReplica
import com.goalmaker.app.data.replica.text
import java.time.Instant
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
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

/**
 * A session the server ends while the app is open (M6-06): the owner goes to sign-in, the outbox
 * stays, and it syncs once the same owner is back. Someone else signing in never gets it.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class AccountSyncTest {
    private lateinit var test: TestReplica
    private val replica get() = test.replica
    private val server = FakeServer()
    private val now = Instant.parse("2026-09-18T12:00:00Z")

    // The fake session: signed in as the replica's owner until the server ends it.
    private val session = MutableStateFlow<AuthSession>(AuthSession.SignedIn(TestReplica.OWNER, "me@example.com"))

    private lateinit var coordinator: SyncCoordinator
    private lateinit var accounts: AccountSync

    @Before
    fun setUp() {
        test = TestReplica()
        // What PostgrestHttp does with a session the server refuses after a refresh.
        server.onSessionEnded = { session.value = AuthSession.SignedOut }
    }

    @After
    fun tearDown() = test.close()

    // The composition root's part: follow the session the way AppGraph does.
    private fun TestScope.start() {
        val io = UnconfinedTestDispatcher(testScheduler)
        coordinator = SyncCoordinator(
            engine = SyncEngine(test.catalog, replica, server) { now },
            replica = replica,
            scope = backgroundScope,
            io = io,
            now = { now },
            debounce = 2.seconds,
        )
        accounts = AccountSync(test.catalog, replica, coordinator, io)
        backgroundScope.launch(io) {
            session.collect { current ->
                when (current) {
                    is AuthSession.SignedIn -> accounts.signedIn(current.userId)
                    AuthSession.SignedOut -> accounts.signedOut()
                    AuthSession.Loading -> Unit
                }
            }
        }
    }

    private fun TestScope.settle() {
        advanceTimeBy(3.seconds)
        runCurrent()
    }

    @Test
    fun `a session the server ended signs out and keeps the outbox`() = runTest {
        start()
        settle()
        replica.queue("tasks", test.newTask("a", "Run"))
        replica.queue("tasks", test.newTask("b", "Read"))
        server.sessionEnded = true

        val report = coordinator.syncNow()

        assertTrue(report.signedOut)
        assertEquals(AuthSession.SignedOut, session.value)
        assertEquals(2, replica.pendingCount())
        assertEquals("Run", replica.get("tasks", "a")?.text("title"))
        assertTrue(server.rows("tasks").isEmpty())
        assertTrue(accounts.sessionEnded())

        // Nothing keeps knocking while nobody is signed in.
        val calls = server.calls
        advanceTimeBy(10.minutes)
        runCurrent()
        assertEquals(calls, server.calls)
    }

    @Test
    fun `signing back in as the same owner syncs what waited`() = runTest {
        start()
        settle()
        replica.queue("tasks", test.newTask("a", "Run"))
        server.sessionEnded = true
        coordinator.syncNow()
        assertEquals(AuthSession.SignedOut, session.value)

        server.sessionEnded = false
        session.value = AuthSession.SignedIn(TestReplica.OWNER, "me@example.com")
        settle()

        assertEquals(0, replica.pendingCount())
        assertEquals(listOf("Run"), server.rows("tasks").map { it.text("title") })
        assertEquals(TestReplica.OWNER, server.rows("tasks").single().text("owner_id"))
        assertEquals(SyncState.IDLE, coordinator.status.value.state)
    }

    @Test
    fun `signing in as someone else never pushes the old owner's outbox`() = runTest {
        start()
        settle()
        replica.queue("tasks", test.newTask("a", "Private"))
        server.sessionEnded = true
        coordinator.syncNow()

        server.sessionEnded = false
        session.value = AuthSession.SignedIn(SOMEONE_ELSE, "else@example.com")
        settle()

        assertTrue(server.rows("tasks").isEmpty())
        assertEquals(0, replica.pendingCount())
        assertTrue(replica.all("tasks").isEmpty())
        assertFalse(accounts.sessionEnded())
    }

    @Test
    fun `an empty replica has no session to have ended`() = runTest {
        start()
        session.value = AuthSession.SignedOut
        settle()

        assertFalse(accounts.sessionEnded())
    }

    private companion object {
        const val SOMEONE_ELSE = "22222222-2222-2222-2222-222222222222"
    }
}
