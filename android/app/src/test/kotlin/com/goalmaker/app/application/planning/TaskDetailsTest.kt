package com.goalmaker.app.application.planning

import android.app.Application
import com.goalmaker.app.data.replica.TestReplica
import com.goalmaker.app.domain.composer.ComposerParser
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Every field of a task's detail view, its checklist, and the archive, on a real replica (M2-12). */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class TaskDetailsTest {
    private lateinit var test: TestReplica
    private lateinit var tasks: TaskList
    private lateinit var tags: TagList
    private lateinit var steps: StepList
    private lateinit var areas: AreaList
    private var now = Instant.parse("2026-09-18T12:00:00Z")

    @Before
    fun setUp() {
        test = TestReplica()
        val rows = NewRows(test.catalog, { TestReplica.OWNER }, { now })
        areas = AreaList(test.replica, rows, listOf("violet", "blue"), {})
        tags = TagList(test.replica, rows, {})
        tasks = TaskList(test.replica, rows, areas, tags, {}) { LocalDate.parse("2026-09-18") }
        steps = StepList(test.replica, rows, {})
    }

    @After
    fun tearDown() = test.close()

    private fun add(line: String): TaskItem = tasks.add(ComposerParser.parse(line, LocalDateTime.parse("2026-09-18T14:00")))!!

    @Test
    fun `title and notes`() {
        val task = add("Book the trip")

        assertTrue(tasks.rename(task.id, "  Book the train  "))
        assertFalse(tasks.rename(task.id, "   "))
        tasks.setNotes(task.id, "Check the **passport**")

        val saved = tasks.find(task.id)!!
        assertEquals("Book the train", saved.title)
        assertEquals("Check the **passport**", saved.notes)
    }

    @Test
    fun `a time needs a day, and a deadline is its own date`() {
        val task = add("Call the bank")

        tasks.schedule(task.id, LocalDate.parse("2026-09-21"), LocalTime.parse("09:30"))
        tasks.setDeadline(task.id, LocalDate.parse("2026-09-25"))
        assertEquals(LocalDate.parse("2026-09-21"), tasks.find(task.id)!!.plannedDate)
        assertEquals(LocalTime.parse("09:30"), tasks.find(task.id)!!.plannedTime)
        assertEquals(LocalDate.parse("2026-09-25"), tasks.find(task.id)!!.deadline)

        tasks.schedule(task.id, null, LocalTime.parse("10:00"))
        assertNull(tasks.find(task.id)!!.plannedDate)
        assertNull(tasks.find(task.id)!!.plannedTime)
        assertEquals(LocalDate.parse("2026-09-25"), tasks.find(task.id)!!.deadline)
    }

    @Test
    fun `area, tags and repeat`() {
        val task = add("Water plants #home")
        val garden = areas.create("Garden")!!

        tasks.setArea(task.id, garden.id)
        tasks.setTags(task.id, listOf("weekend", "home"))
        assertTrue(tasks.setRecurrence(task.id, "FREQ=WEEKLY;BYDAY=SA"))
        assertFalse(tasks.setRecurrence(task.id, "FREQ=YEARLY"))

        val saved = tasks.find(task.id)!!
        assertEquals(garden.id, saved.areaId)
        assertEquals(setOf("home", "weekend"), tags.forTask(task.id).map(TagItem::name).toSet())
        assertEquals("FREQ=WEEKLY;BYDAY=SA", saved.recurrence)
        assertEquals(task.id, saved.seriesId)

        tasks.setTags(task.id, listOf("weekend"))
        tasks.setRecurrence(task.id, null)
        assertEquals(listOf("weekend"), tags.forTask(task.id).map(TagItem::name))
        assertNull(tasks.find(task.id)!!.recurrence)
    }

    @Test
    fun `a checklist keeps its order, and steps are checked, renamed, moved and deleted`() {
        val task = add("Pack for the trip")
        val socks = steps.add(task.id, "Socks")!!
        steps.add(task.id, "Charger")
        val passport = steps.add(task.id, "Passport")!!
        assertEquals(null, steps.add(task.id, "  "))

        steps.setDone(socks.id, true)
        assertTrue(steps.rename(passport.id, "Passport and tickets"))
        steps.move(passport.id, 0)
        assertEquals(listOf("Passport and tickets", "Socks", "Charger"), steps.forTask(task.id).map(StepItem::title))
        assertTrue(steps.forTask(task.id).first { it.id == socks.id }.done)

        steps.delete(socks.id)
        assertEquals(listOf("Passport and tickets", "Charger"), steps.forTask(task.id).map(StepItem::title))
    }

    @Test
    fun `the archive finds a done task by a word of its title and reopens it`() {
        val bank = add("Call the bank")
        val milk = add("Buy milk")
        tasks.setDone(bank.id, true)
        now = Instant.parse("2026-09-18T13:00:00Z")
        tasks.setDone(milk.id, true)

        assertEquals(listOf("Buy milk", "Call the bank"), ArchiveRules.search(tasks.all(), "").map(TaskItem::title))
        assertEquals(listOf(bank.id), ArchiveRules.search(tasks.all(), "bank").map(TaskItem::id))

        tasks.setDone(bank.id, false)
        assertEquals(listOf(milk.id), ArchiveRules.search(tasks.all(), "").map(TaskItem::id))
        assertEquals(TaskState.OPEN, tasks.find(bank.id)!!.state)
    }
}
