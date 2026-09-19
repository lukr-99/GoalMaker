package com.goalmaker.app.application.planning

import android.app.Application
import com.goalmaker.app.data.replica.TestReplica
import com.goalmaker.app.domain.composer.ComposerParser
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
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

/** Goals on a real replica (docs/goals.md, M4-02). */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class GoalListTest {
    private lateinit var test: TestReplica
    private lateinit var goals: GoalList
    private lateinit var tasks: TaskList

    @Before
    fun setUp() {
        test = TestReplica()
        val rows = NewRows(test.catalog, { TestReplica.OWNER }, { Instant.parse("2026-09-18T12:00:00Z") })
        val areas = AreaList(test.replica, rows, listOf("violet"), {})
        val tags = TagList(test.replica, rows, {})
        goals = GoalList(test.replica, rows, {})
        tasks = TaskList(test.replica, rows, areas, tags, {}) { LocalDate.parse("2026-09-18") }
    }

    @After
    fun tearDown() = test.close()

    @Test
    fun `a goal lands on its period's first day and keeps only a parent it can serve`() {
        val year = goals.add(GoalDraft("Run a half marathon", GoalHorizon.YEAR, LocalDate.parse("2026-05-02")))!!
        val lastYear = goals.add(GoalDraft("Old", GoalHorizon.YEAR, LocalDate.parse("2025-03-01")))!!

        val month = goals.add(GoalDraft("Run 80 km", GoalHorizon.MONTH, LocalDate.parse("2026-09-18"), GoalRules.MODE_NUMBER, parentId = year.id, target = 80.0, unit = "km"))!!
        val stray = goals.add(GoalDraft("3 runs", GoalHorizon.WEEK, LocalDate.parse("2026-09-18"), parentId = lastYear.id))!!

        assertEquals(LocalDate.parse("2026-01-01"), year.periodStart)
        assertEquals(LocalDate.parse("2026-09-01"), month.periodStart)
        assertEquals(year.id, month.parentId)
        assertEquals(LocalDate.parse("2026-09-14"), stray.periodStart)
        assertNull(stray.parentId)
    }

    @Test
    fun `a numeric goal needs a positive target, and a blank title is refused`() {
        assertNull(goals.add(GoalDraft("Run", GoalHorizon.MONTH, LocalDate.parse("2026-09-01"), GoalRules.MODE_NUMBER)))
        assertNull(goals.add(GoalDraft("Run", GoalHorizon.MONTH, LocalDate.parse("2026-09-01"), GoalRules.MODE_NUMBER, target = 0.0)))
        assertNull(goals.add(GoalDraft("  ", GoalHorizon.MONTH, LocalDate.parse("2026-09-01"))))
        assertTrue(goals.all().isEmpty())
    }

    @Test
    fun `amounts, linked tasks and the done status drive progress`() {
        val run = goals.add(GoalDraft("Run 20 km", GoalHorizon.WEEK, LocalDate.parse("2026-09-14"), GoalRules.MODE_NUMBER, target = 20.0, unit = "km"))!!
        assertNotNull(goals.logAmount(run.id, LocalDate.parse("2026-09-15"), 5.0))
        assertNotNull(goals.logAmount(run.id, LocalDate.parse("2026-09-17"), 10.0))
        assertNull(goals.logAmount(run.id, LocalDate.parse("2026-09-17"), 0.0))
        val entries = goals.entries().filter { it.goalId == run.id }
        assertEquals(0.75, GoalRules.progress(run.mode, run.status, run.target, emptyList(), entries).fraction, 1e-9)

        val fix = goals.add(GoalDraft("Fix the bike", GoalHorizon.WEEK, LocalDate.parse("2026-09-14"), GoalRules.MODE_TASKS))!!
        val first = tasks.add(ComposerParser.parse("Buy a chain", LocalDateTime.parse("2026-09-18T12:00")))!!
        tasks.add(ComposerParser.parse("Oil it", LocalDateTime.parse("2026-09-18T12:00")))!!.also { tasks.setGoal(it.id, fix.id) }
        tasks.setGoal(first.id, fix.id)
        tasks.setDone(first.id, true)
        val serving = tasks.all().filter { it.goalId == fix.id }
        assertEquals(0.5, GoalRules.progress(fix.mode, fix.status, fix.target, serving, emptyList()).fraction, 1e-9)

        assertTrue(goals.setStatus(fix.id, GoalRules.DONE))
        val done = goals.find(fix.id)!!
        assertNotNull(done.completedAt)
        assertTrue(GoalRules.progress(done.mode, done.status, done.target, serving, emptyList()).hit)
    }

    @Test
    fun `last week's goals are copied once into an empty week`() {
        val month = goals.add(GoalDraft("Get fit", GoalHorizon.MONTH, LocalDate.parse("2026-09-01")))!!
        goals.add(GoalDraft("3 runs", GoalHorizon.WEEK, LocalDate.parse("2026-09-14"), parentId = month.id))
        goals.add(GoalDraft("Read", GoalHorizon.WEEK, LocalDate.parse("2026-09-14")))!!.also { goals.setStatus(it.id, GoalRules.DROPPED) }

        assertEquals(1, goals.copyPrevious(GoalHorizon.WEEK, LocalDate.parse("2026-09-21")))
        assertEquals(0, goals.copyPrevious(GoalHorizon.WEEK, LocalDate.parse("2026-09-21")))

        val copied = goals.all().single { it.periodStart == LocalDate.parse("2026-09-21") }
        assertEquals("3 runs", copied.title)
        assertEquals(month.id, copied.parentId)
        assertEquals(GoalRules.OPEN, copied.status)
    }

    @Test
    fun `a deleted goal leaves the list`() {
        val goal = goals.add(GoalDraft("Read", GoalHorizon.DAY, LocalDate.parse("2026-09-18")))!!
        assertTrue(goals.delete(goal.id))
        assertTrue(goals.all().isEmpty())
        assertFalse(goals.update(goal.id, GoalDraft("Read more", GoalHorizon.DAY, LocalDate.parse("2026-09-18"))))
    }

    @Test
    fun `a repeating task's next occurrence keeps its goal only inside the goal's period`() {
        val week = goals.add(GoalDraft("3 runs", GoalHorizon.WEEK, LocalDate.parse("2026-09-14"), GoalRules.MODE_TASKS))!!
        val friday = tasks.add(ComposerParser.parse("Run daily", LocalDateTime.parse("2026-09-18T08:00")))!!
        tasks.setGoal(friday.id, week.id)

        tasks.setDone(friday.id, true)
        val saturday = tasks.all().single { it.state == TaskState.OPEN }
        tasks.setDone(saturday.id, true)
        val sunday = tasks.all().single { it.state == TaskState.OPEN }
        tasks.setDone(sunday.id, true)
        val monday = tasks.all().single { it.state == TaskState.OPEN }

        assertEquals(LocalDate.parse("2026-09-19") to week.id, saturday.plannedDate to saturday.goalId)
        assertEquals(LocalDate.parse("2026-09-20") to week.id, sunday.plannedDate to sunday.goalId)
        assertEquals(LocalDate.parse("2026-09-21") to null, monday.plannedDate to monday.goalId)
    }
}
