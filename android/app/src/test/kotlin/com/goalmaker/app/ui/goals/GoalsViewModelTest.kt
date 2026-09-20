package com.goalmaker.app.ui.goals

import android.app.Application
import android.os.Looper
import com.goalmaker.app.application.planning.AreaList
import com.goalmaker.app.application.planning.GoalDraft
import com.goalmaker.app.application.planning.GoalHorizon
import com.goalmaker.app.application.planning.GoalList
import com.goalmaker.app.application.planning.GoalRules
import com.goalmaker.app.application.planning.HabitList
import com.goalmaker.app.application.planning.NewRows
import com.goalmaker.app.application.planning.ProjectList
import com.goalmaker.app.application.planning.TagList
import com.goalmaker.app.application.planning.TaskList
import com.goalmaker.app.data.replica.TestReplica
import com.goalmaker.app.domain.composer.ComposerParser
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/** The Goals screen over a real replica: periods, progress, the cascade and copying (M4-02). */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class GoalsViewModelTest {
    private lateinit var test: TestReplica
    private lateinit var goals: GoalList
    private lateinit var tasks: TaskList
    private lateinit var viewModel: GoalsViewModel

    // Saturday 19 September 2026, 01:30: with the day starting at 04:00 it is still Friday the 18th.
    private val now = LocalDateTime.parse("2026-09-19T01:30")

    @Before
    fun setUp() {
        test = TestReplica()
        val rows = NewRows(test.catalog, { TestReplica.OWNER }, { Instant.parse("2026-09-18T12:00:00Z") })
        val areas = AreaList(test.replica, rows, listOf("violet"), {})
        val tags = TagList(test.replica, rows, {})
        goals = GoalList(test.replica, rows, {})
        tasks = TaskList(test.replica, rows, areas, tags, ProjectList(test.replica, rows, {}), {}) { LocalDate.parse("2026-09-18") }
        viewModel = GoalsViewModel(goals, tasks, HabitList(test.replica, rows, {}), MutableStateFlow(4), Dispatchers.Unconfined) { now }
    }

    @After
    fun tearDown() = test.close()

    @Test
    fun `the sections are this year, month, week and planning day, then next week`() = runTest {
        val state = viewModel.uiState.first { it.loaded }

        assertEquals(LocalDate.parse("2026-09-18"), state.today)
        assertEquals(
            listOf(
                GoalHorizon.YEAR to "2026-01-01",
                GoalHorizon.MONTH to "2026-09-01",
                GoalHorizon.WEEK to "2026-09-14",
                GoalHorizon.DAY to "2026-09-18",
                GoalHorizon.WEEK to "2026-09-21",
            ),
            state.sections.map { it.horizon to it.start.toString() },
        )
        assertEquals(listOf(false, false, false, false, true), state.sections.map { it.next })
    }

    @Test
    fun `progress counts the tasks that serve a goal and the amounts logged on it`() = runTest {
        val runs = goals.add(GoalDraft("3 runs", GoalHorizon.WEEK, LocalDate.parse("2026-09-14"), GoalRules.MODE_TASKS))!!
        val km = goals.add(GoalDraft("Run 80 km", GoalHorizon.MONTH, LocalDate.parse("2026-09-01"), GoalRules.MODE_NUMBER, target = 80.0, unit = "km"))!!
        val first = tasks.add(ComposerParser.parse("Morning run", now))!!
        val second = tasks.add(ComposerParser.parse("Long run", now))!!
        tasks.setGoal(first.id, runs.id)
        tasks.setGoal(second.id, runs.id)
        tasks.setDone(first.id, true)
        viewModel.logAmount(km.id, 12.5)
        viewModel.logAmount(km.id, 7.5)

        val state = viewModel.uiState.first { state -> state.sections.flatMap { it.rows }.size == 2 && state.hits.isEmpty() }
        val week = state.sections[2].rows.single()
        val month = state.sections[1].rows.single()
        assertEquals(1.0 to 2.0, week.progress.value to week.progress.target)
        assertEquals(0.5, week.progress.fraction, 0.0)
        assertEquals(20.0 to 80.0, month.progress.value to month.progress.target)
        assertEquals(LocalDate.parse("2026-09-18"), goals.entries().first().day)
    }

    @Test
    fun `marking a done-or-not goal done makes it a hit, and dropping one takes it off`() = runTest {
        val book = goals.add(GoalDraft("Book the race", GoalHorizon.WEEK, LocalDate.parse("2026-09-14")))!!
        val old = goals.add(GoalDraft("Old idea", GoalHorizon.WEEK, LocalDate.parse("2026-09-14")))!!

        viewModel.setStatus(book.id, GoalRules.DONE)
        viewModel.setStatus(old.id, GoalRules.DROPPED)

        val state = viewModel.uiState.first { it.hits == setOf(book.id) }
        assertEquals(listOf("Book the race"), state.sections[2].rows.map { it.goal.title })
    }

    @Test
    fun `the tree puts each goal under the one it serves`() = runTest {
        val year = goals.add(GoalDraft("Half marathon", GoalHorizon.YEAR, LocalDate.parse("2026-01-01")))!!
        val month = goals.add(GoalDraft("Run 80 km", GoalHorizon.MONTH, LocalDate.parse("2026-09-01"), GoalRules.MODE_NUMBER, parentId = year.id, target = 80.0))!!
        goals.add(GoalDraft("3 runs", GoalHorizon.WEEK, LocalDate.parse("2026-09-14"), parentId = month.id))!!
        goals.add(GoalDraft("Read a book", GoalHorizon.MONTH, LocalDate.parse("2026-09-01")))!!

        viewModel.showTree(true)
        val state = viewModel.uiState.first { it.showTree && it.tree.size == 4 }

        assertEquals(
            listOf("Half marathon" to 0, "Run 80 km" to 1, "3 runs" to 2, "Read a book" to 0),
            state.tree.map { it.goal.title to it.depth },
        )
        assertEquals("Run 80 km", state.sections[2].rows.single().parentTitle)
    }

    @Test
    fun `an empty week offers last week's goals, and copying brings them back open`() = runTest {
        val kept = goals.add(GoalDraft("3 runs", GoalHorizon.WEEK, LocalDate.parse("2026-09-07")))!!
        goals.setStatus(kept.id, GoalRules.DONE)
        val dropped = goals.add(GoalDraft("Old idea", GoalHorizon.WEEK, LocalDate.parse("2026-09-07")))!!
        goals.setStatus(dropped.id, GoalRules.DROPPED)

        val before = viewModel.uiState.first { it.loaded }
        val week = before.sections[2]
        assertTrue(week.canCopy)
        assertFalse(before.sections[4].canCopy)

        viewModel.copyPrevious(week)
        shadowOf(Looper.getMainLooper()).idle()

        val after = viewModel.uiState.first { it.sections[2].rows.isNotEmpty() }
        assertEquals(listOf("3 runs" to GoalRules.OPEN), after.sections[2].rows.map { it.goal.title to it.goal.status })
        assertFalse(after.sections[2].canCopy)
        assertTrue(after.sections[4].canCopy)
    }

    @Test
    fun `saving checks the goal, and editing changes it`() = runTest {
        assertFalse(viewModel.save(null, GoalDraft(" ", GoalHorizon.WEEK, LocalDate.parse("2026-09-14"))))
        assertTrue(viewModel.save(null, GoalDraft("Run", GoalHorizon.WEEK, LocalDate.parse("2026-09-16"))))
        val goal = goals.all().single()
        assertEquals(LocalDate.parse("2026-09-14"), goal.periodStart)

        assertTrue(viewModel.save(goal.id, GoalDraft("Run 20 km", GoalHorizon.WEEK, goal.periodStart, GoalRules.MODE_NUMBER, "🏃", target = 20.0, unit = "km")))

        val edited = goals.find(goal.id)!!
        assertEquals(listOf("Run 20 km", "🏃", "km"), listOf(edited.title, edited.emoji, edited.unit))
        assertEquals(20.0, edited.target!!, 0.0)
    }
}
