package com.goalmaker.app.application.sync

import android.app.Application
import com.goalmaker.app.data.replica.TestReplica
import com.goalmaker.app.data.replica.text
import com.goalmaker.app.data.replica.with
import com.goalmaker.app.data.sync.LocalOnlyRemoteTables
import java.time.Instant
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonPrimitive
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class SyncEngineTest {
    private lateinit var test: TestReplica
    private val replica get() = test.replica
    private val server = FakeServer()
    private val now = Instant.parse("2026-09-18T12:00:00Z")

    private fun engine() = SyncEngine(test.catalog, replica, server) { now }

    @Before
    fun setUp() {
        test = TestReplica()
    }

    @After
    fun tearDown() = test.close()

    @Test
    fun `a local task reaches the server and comes back stamped`() = runTest {
        replica.queue("tasks", test.newTask("a", "Run"))

        val report = engine().run()

        assertEquals(1, report.pushed)
        assertTrue(replica.outbox().isEmpty())
        assertTrue(Regex("2026-09-18T10:00:00\\.[0-9]{6}Z").matches(replica.get("tasks", "a")!!.text("updated_at")!!))
        assertEquals("Run", server.rows("tasks").single().text("title"))
    }

    @Test
    fun `a dev build's local-only sync keeps its rows run after run`() = runTest {
        // Pulling from a remote that never sends anything back would be a full resync every run,
        // clearing the replica; a local-only engine only pushes (docs/sign-in.md).
        val local = SyncEngine(test.catalog, replica, LocalOnlyRemoteTables { now }, pulls = false) { now }
        replica.queue("tasks", test.newTask("a", "Run"))

        local.run()
        local.run()

        assertTrue(replica.outbox().isEmpty())
        assertEquals("Run", replica.get("tasks", "a")!!.text("title"))
    }

    @Test
    fun `rows from another device arrive`() = runTest {
        server.seed("tasks", test.newTask("remote", "From the PC"))

        val report = engine().run()

        assertEquals(1, report.pulled)
        assertEquals("From the PC", replica.get("tasks", "remote")!!.text("title"))
        assertNotNull(replica.watermark("tasks"))
    }

    @Test
    fun `many rows arrive across pages`() = runTest {
        val count = SyncEngine.PAGE_SIZE * 2 + 17
        repeat(count) { server.seed("tasks", test.newTask("t%05d".format(it), "Task $it")) }

        engine().run()

        assertEquals(count, replica.all("tasks").size)
    }

    @Test
    fun `a second sync only fetches the overlap`() = runTest {
        server.seed("tasks", test.newTask("a", "One"))
        engine().run()
        server.advance(5 * 60)
        server.seed("tasks", test.newTask("b", "Two"))

        val report = engine().run()

        // "a" again (inside the 60-second overlap, merged as a no-op) and the new "b".
        assertEquals(2, report.pulled)
        assertNotNull(replica.get("tasks", "b"))
    }

    @Test
    fun `a pending local edit is not overwritten by a pull`() = runTest {
        val row = test.newTask("a", "Server title")
        server.seed("tasks", row)
        engine().run()
        replica.queue("tasks", replica.get("tasks", "a")!!.with("title" to "Local title"))
        server.seed("tasks", row.with("title" to "Changed on the PC"))
        server.refusedIds += "a"

        engine().run()

        assertEquals("Local title", replica.get("tasks", "a")!!.text("title"))
        assertEquals(1, replica.outbox().size)
    }

    @Test
    fun `a deletion elsewhere beats a pending edit here`() = runTest {
        val row = test.newTask("a", "Run")
        server.seed("tasks", row)
        engine().run()
        replica.queue("tasks", replica.get("tasks", "a")!!.with("title" to "Run 5 km"))
        server.seed("tasks", row.with("deleted_at" to "2026-09-18T11:00:00.000000Z"))
        server.refusedIds += "a"

        engine().run()

        assertNotNull(replica.get("tasks", "a")!!.text("deleted_at"))
        assertTrue(replica.outbox().isEmpty())
    }

    @Test
    fun `a refused row is recorded and the rest still sync`() = runTest {
        replica.queue("tasks", test.newTask("bad", "Refused"))
        replica.queue("tasks", test.newTask("good", "Accepted"))
        server.refusedIds += "bad"

        val report = engine().run()

        assertEquals(1, report.pushed)
        assertEquals(1, report.rejected)
        val left = replica.outbox().single()
        assertEquals("bad", left.rowId)
        assertEquals(1, left.attempts)
        assertTrue(left.lastError!!.contains("403"))
    }

    @Test
    fun `offline keeps everything for later`() = runTest {
        replica.queue("tasks", test.newTask("a", "Run"))
        server.offline = true

        val report = engine().run()

        assertTrue(report.offline)
        assertEquals(1, replica.outbox().size)
        assertEquals(0, report.pushed)
    }

    @Test
    fun `a stale device starts over and drops rows purged meanwhile`() = runTest {
        server.seed("tasks", test.newTask("kept", "Still there"))
        engine().run()
        replica.put("tasks", test.newTask("purged", "Deleted and purged while this device was away"))
        replica.setWatermark("tasks", "2026-06-01T00:00:00.000000Z")

        engine().run()

        assertNull(replica.get("tasks", "purged"))
        assertNotNull(replica.get("tasks", "kept"))
    }

    @Test
    fun `server timestamps are stored normalized`() = runTest {
        server.seed("tasks", test.newTask("a", "Done").with("status" to "done", "completed_at" to "2026-09-18T12:30:00+02:00"))

        engine().run()

        assertEquals("2026-09-18T10:30:00.000000Z", replica.get("tasks", "a")!!.text("completed_at"))
    }

    @Test
    fun `normalize keeps every described column and nothing else`() {
        val row = test.newTask("a", "Run").with("surprise" to "from a newer server")

        val normalized = SyncEngine.normalize(test.catalog["tasks"], row)

        assertFalse(normalized.containsKey("surprise"))
        assertEquals(test.catalog["tasks"].columns.map { it.name }, normalized.keys.toList())
        assertEquals(JsonPrimitive("Run"), normalized["title"])
    }
}
