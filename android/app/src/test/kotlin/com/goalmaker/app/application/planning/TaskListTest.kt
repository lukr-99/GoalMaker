package com.goalmaker.app.application.planning

import android.app.Application
import com.goalmaker.app.data.replica.TestReplica
import com.goalmaker.app.data.replica.text
import java.time.Instant
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
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
class TaskListTest {
    private lateinit var test: TestReplica
    private var now = Instant.parse("2026-09-18T12:00:00Z")
    private var syncRequests = 0

    private fun tasks(owner: String? = TestReplica.OWNER) =
        TaskList(test.catalog, test.replica, { owner }, { now }, { syncRequests++ })

    @Before
    fun setUp() {
        test = TestReplica()
    }

    @After
    fun tearDown() = test.close()

    @Test
    fun `adding queues a complete row and asks for a sync`() {
        val added = tasks().add("  Call the dentist  ")

        assertNotNull(added)
        assertEquals("Call the dentist", added!!.title)
        val row = test.replica.get("tasks", added.id)!!
        assertEquals(TestReplica.OWNER, row.text("owner_id"))
        assertEquals("2026-09-18T12:00:00.000000Z", row.text("created_at"))
        assertEquals(test.catalog["tasks"].columns.size, row.size)
        assertEquals(1, test.replica.outbox().size)
        assertEquals(1, syncRequests)
    }

    @Test
    fun `blank titles and signed-out adds are ignored`() {
        assertNull(tasks().add("   "))
        assertNull(tasks(owner = null).add("Run"))
        assertTrue(test.replica.outbox().isEmpty())
    }

    @Test
    fun `done and deleted tasks leave the open list`() {
        val tasks = tasks()
        val run = tasks.add("Run")!!
        val read = tasks.add("Read")!!
        now = now.plusSeconds(60)

        tasks.setDone(run.id, true)
        tasks.delete(read.id)

        assertTrue(tasks.open().isEmpty())
        val done = test.replica.get("tasks", run.id)!!
        assertEquals("done", done.text("status"))
        assertEquals("2026-09-18T12:01:00.000000Z", done.text("completed_at"))
        assertEquals("2026-09-18T12:01:00.000000Z", test.replica.get("tasks", read.id)!!.text("deleted_at"))
    }

    @Test
    fun `reopening clears the completion time`() {
        val tasks = tasks()
        val run = tasks.add("Run")!!
        tasks.setDone(run.id, true)
        tasks.setDone(run.id, false)

        assertEquals(1, tasks.open().size)
        assertNull(test.replica.get("tasks", run.id)!!.text("completed_at"))
    }

    @Test
    fun `the open list follows changes`() = runTest {
        val tasks = tasks()
        assertTrue(tasks.watchOpen().first().isEmpty())

        tasks.add("Run")

        assertEquals(listOf("Run"), tasks.watchOpen().first().map(TaskItem::title))
    }
}
