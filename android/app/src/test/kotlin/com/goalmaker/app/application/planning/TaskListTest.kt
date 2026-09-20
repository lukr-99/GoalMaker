package com.goalmaker.app.application.planning

import android.app.Application
import com.goalmaker.app.data.replica.TestReplica
import com.goalmaker.app.data.replica.text
import com.goalmaker.app.domain.composer.ComposerParser
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonNull
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

    private fun tasks(owner: String? = TestReplica.OWNER): TaskList {
        val rows = NewRows(test.catalog, { owner }, { now })
        val areas = AreaList(test.replica, rows, listOf("violet", "blue", "cyan"), { syncRequests++ })
        return TaskList(test.replica, rows, areas, TagList(test.replica, rows, { syncRequests++ }), ProjectList(test.replica, rows, { syncRequests++ }), { syncRequests++ }) {
            LocalDate.parse("2026-09-18")
        }
    }

    private fun draft(line: String) = ComposerParser.parse(line, LocalDateTime.parse("2026-09-18T14:05"))

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
        // The server refuses a row that carries a null where its column is not null, and a null
        // defeats the column's default. RequiredColumnsTest walks every list this way.
        test.catalog["tasks"].required.forEach { column ->
            assertTrue("$column needs a value", row[column].let { it != null && it != JsonNull })
        }
        assertEquals(1, test.replica.outbox().size)
        assertEquals(1, syncRequests)
    }

    @Test
    fun `blank titles and signed-out adds are ignored`() {
        assertNull(tasks().add("   "))
        assertNull(tasks().add(draft("tomorrow #run")))
        assertNull(tasks(owner = null).add("Run"))
        assertTrue(test.replica.outbox().isEmpty())
    }

    @Test
    fun `a composer line saves its day, time, priority and repeat`() {
        val added = tasks().add(draft("Standup weekdays 9:30 !"))!!

        val row = test.replica.get("tasks", added.id)!!
        assertEquals("Standup", row.text("title"))
        assertEquals("2026-09-21", row.text("planned_date"))
        assertEquals("09:30:00", row.text("planned_time"))
        assertEquals("true", row["top_priority"].toString())
        assertEquals("FREQ=WEEKLY;BYDAY=MO,TU,WE,TH,FR", row.text("recurrence"))
    }

    @Test
    fun `a new area gets the first unused palette color and is saved before the task`() {
        val tasks = tasks()
        tasks.add(draft("Stretch @Health"))
        val second = tasks.add(draft("Read @School"))!!

        val areas = test.replica.all("areas").associate { it.text("name") to it.text("color") }
        assertEquals(mapOf("Health" to "violet", "School" to "blue"), areas)
        assertEquals(listOf("areas", "tasks", "areas", "tasks"), test.replica.outbox().map { it.entity })
        assertEquals(test.replica.all("areas").first { it.text("name") == "School" }.text("id"), second.areaId)
    }

    @Test
    fun `an existing area is reused whatever its case`() {
        val tasks = tasks()
        val first = tasks.add(draft("Stretch @Health"))!!
        val second = tasks.add(draft("Run @health"))!!

        assertEquals(1, test.replica.all("areas").size)
        assertEquals(first.areaId, second.areaId)
    }

    @Test
    fun `tags are created once and linked to the task`() {
        val tasks = tasks()
        val first = tasks.add(draft("Run #health #run"))!!
        tasks.add(draft("Swim #Health"))

        assertEquals(listOf("health", "run"), test.replica.all("tags").map { it.text("name") }.sortedBy { it })
        val links = test.replica.all("task_tags").filter { it.text("task_id") == first.id }
        assertEquals(2, links.size)
        assertEquals(3, test.replica.all("task_tags").size)
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
    fun `planning reopens a task on its new day and keeps its time`() {
        val tasks = tasks()
        val call = tasks.add(draft("Call mum today 18:00"))!!
        tasks.setDone(call.id, true)
        tasks.plan(call.id, LocalDate.parse("2026-09-19"))

        val row = test.replica.get("tasks", call.id)!!
        assertEquals("2026-09-19", row.text("planned_date"))
        assertEquals("18:00:00", row.text("planned_time"))
        assertEquals("open", row.text("status"))
        assertNull(row.text("completed_at"))
    }

    @Test
    fun `dropping keeps the task but closes it`() {
        val tasks = tasks()
        val run = tasks.add("Run")!!
        tasks.setDone(run.id, true)
        tasks.drop(run.id)

        val row = test.replica.get("tasks", run.id)!!
        assertEquals("dropped", row.text("status"))
        assertNull(row.text("completed_at"))
        assertNull(row.text("deleted_at"))
        assertTrue(tasks.open().isEmpty())
        assertEquals(TaskState.DROPPED, tasks.all().single().state)
    }

    @Test
    fun `top priority can be set and cleared`() {
        val tasks = tasks()
        val run = tasks.add("Run")!!
        tasks.setTopPriority(run.id, true)
        assertTrue(tasks.all().single().topPriority)
        tasks.setTopPriority(run.id, false)
        assertEquals(false, tasks.all().single().topPriority)
    }

    @Test
    fun `the open list follows changes`() = runTest {
        val tasks = tasks()
        assertTrue(tasks.watchOpen().first().isEmpty())

        tasks.add("Run")

        assertEquals(listOf("Run"), tasks.watchOpen().first().map(TaskItem::title))
    }
}
