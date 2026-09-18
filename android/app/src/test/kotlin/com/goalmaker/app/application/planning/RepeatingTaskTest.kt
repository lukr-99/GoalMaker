package com.goalmaker.app.application.planning

import android.app.Application
import com.goalmaker.app.application.sync.FakeServer
import com.goalmaker.app.application.sync.SyncEngine
import com.goalmaker.app.data.replica.TestReplica
import com.goalmaker.app.data.replica.text
import com.goalmaker.app.domain.composer.ComposerParser
import com.goalmaker.app.domain.planning.PlanningDay
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Repeating tasks on one device and across two (docs/repeating.md). */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class RepeatingTaskTest {
    private var now = Instant.parse("2026-09-18T12:00:00Z")
    private val server = FakeServer()
    private val phone = Device()
    private val pc = Device()

    @After
    fun tearDown() {
        phone.close()
        pc.close()
    }

    @Test
    fun `finishing makes the next occurrence with the same plan`() {
        val run = phone.add("Run daily 19:00 #health @Home !")

        phone.tasks.setDone(run.id, true)

        val next = phone.task(Occurrences.successorId(run.id))
        assertEquals(LocalDate.parse("2026-09-19"), next.plannedDate)
        assertEquals(LocalTime.of(19, 0), next.plannedTime)
        assertEquals(Triple("Run", TaskState.OPEN, true), Triple(next.title, next.state, next.topPriority))
        assertEquals(run.areaId, next.areaId)
        assertEquals("FREQ=DAILY", next.recurrence)
        assertEquals(run.id, run.seriesId)
        assertEquals(run.id, next.seriesId)
        val link = phone.test.replica.get("task_tags", Occurrences.tagLinkId(next.id, phone.tagLinks(run.id).single()))
        assertEquals(next.id, link!!.text("task_id"))
    }

    @Test
    fun `dropping moves on too`() {
        val review = phone.add("Review budget every friday")

        phone.tasks.drop(review.id)

        assertEquals(LocalDate.parse("2026-09-25"), phone.task(Occurrences.successorId(review.id)).plannedDate)
    }

    @Test
    fun `reopening takes the next occurrence back and finishing again brings it back`() {
        val run = phone.add("Run daily")
        val nextId = Occurrences.successorId(run.id)

        phone.tasks.setDone(run.id, true)
        phone.tasks.setDone(run.id, false)
        assertEquals(listOf(run.id), phone.openIds())
        assertNotNull(phone.test.replica.get("tasks", nextId)!!.text("deleted_at"))

        phone.tasks.drop(run.id)
        assertEquals(listOf(nextId), phone.openIds())

        phone.tasks.plan(run.id, LocalDate.parse("2026-09-19"))
        assertEquals(listOf(run.id), phone.openIds())
    }

    @Test
    fun `changing how it finished doesn't make a second occurrence`() {
        val run = phone.add("Run daily")

        phone.tasks.setDone(run.id, true)
        phone.tasks.drop(run.id)

        assertEquals(1, phone.tasks.open().size)
        assertEquals(2, phone.tasks.all().size)
    }

    @Test
    fun `tasks that don't repeat just finish`() {
        val call = phone.add("Call the bank")
        val odd = phone.add("Stretch")
        val row = phone.test.replica.get("tasks", odd.id)!!
        phone.test.replica.queue("tasks", JsonObject(row + ("recurrence" to JsonPrimitive("FREQ=YEARLY"))))

        phone.tasks.setDone(call.id, true)
        phone.tasks.setDone(odd.id, true)

        assertTrue(phone.tasks.open().isEmpty())
        assertEquals(2, phone.tasks.all().size)
    }

    @Test
    fun `both devices finishing the same occurrence make one next`() = runTest {
        val run = phone.add("Run daily")
        phone.sync()
        pc.sync()

        phone.tasks.setDone(run.id, true)
        pc.tasks.drop(run.id)
        phone.sync()
        pc.sync()
        phone.sync()

        assertEquals(listOf(Occurrences.successorId(run.id)), phone.openIds())
        assertEquals(phone.openIds(), pc.openIds())
    }

    @Test
    fun `an offline device never leaves two open occurrences`() = runTest {
        val run = phone.add("Run daily")
        phone.sync()
        pc.sync()

        // The phone finishes two days in a row while the PC is offline and finishes the first again.
        phone.tasks.setDone(run.id, true)
        val second = Occurrences.successorId(run.id)
        phone.tasks.setDone(second, true)
        val third = Occurrences.successorId(second)
        phone.sync()
        pc.tasks.setDone(run.id, true)

        pc.sync()
        phone.sync()
        pc.sync()

        assertEquals(listOf(third), phone.openIds())
        assertEquals(listOf(third), pc.openIds())
        assertEquals(listOf(third), server.rows("tasks").filter { it.text("status") == "open" && it.text("deleted_at") == null }.map { it.text("id") })
        assertEquals(TaskState.DROPPED, pc.task(second).state)
    }

    /** A device: its own replica and task list, syncing with the shared fake server. */
    private inner class Device : AutoCloseable {
        val test = TestReplica()
        private val rows = NewRows(test.catalog, { TestReplica.OWNER }, { now })
        val tasks = TaskList(
            test.replica,
            rows,
            AreaList(test.replica, rows, listOf("violet", "blue"), {}),
            TagList(test.replica, rows, {}),
            {},
            { PlanningDay.of(now.atOffset(ZoneOffset.UTC).toLocalDateTime()) },
        )
        private val engine = SyncEngine(test.catalog, test.replica, server) { now }

        fun add(line: String): TaskItem {
            val added = tasks.add(ComposerParser.parse(line, now.atOffset(ZoneOffset.UTC).toLocalDateTime()))!!
            now = now.plusSeconds(1)
            return task(added.id)
        }

        fun task(id: String): TaskItem = tasks.all().single { it.id == id }

        fun tagLinks(taskId: String): List<String> =
            test.replica.all("task_tags").filter { it.text("task_id") == taskId }.mapNotNull { it.text("tag_id") }

        fun openIds(): List<String> = tasks.open().map(TaskItem::id)

        // What the apps do: run, repair when rows came in, and push the repair.
        suspend fun sync() {
            val report = engine.run()
            assertEquals(0, report.rejected)
            if (report.pulled > 0 && tasks.repairSeries()) engine.run()
            now = now.plusSeconds(1)
        }

        override fun close() = test.close()
    }
}
