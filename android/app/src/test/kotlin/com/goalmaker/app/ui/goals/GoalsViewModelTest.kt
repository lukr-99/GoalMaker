package com.goalmaker.app.ui.goals

import android.app.Application
import android.content.Context
import android.os.Looper
import androidx.core.content.edit
import com.goalmaker.app.application.planning.AreaList
import com.goalmaker.app.application.planning.GoalDraft
import com.goalmaker.app.application.planning.GoalHorizon
import com.goalmaker.app.application.planning.GoalList
import com.goalmaker.app.application.planning.GoalPace
import com.goalmaker.app.application.planning.GoalProgress
import com.goalmaker.app.application.planning.GoalRules
import com.goalmaker.app.application.planning.HabitList
import com.goalmaker.app.application.planning.NewRows
import com.goalmaker.app.application.planning.ProjectList
import com.goalmaker.app.application.planning.TagList
import com.goalmaker.app.application.planning.TaskList
import com.goalmaker.app.data.replica.TestReplica
import com.goalmaker.app.data.settings.SharedPreferencesSettingsStore
import com.goalmaker.app.domain.composer.ComposerParser
import com.goalmaker.app.domain.settings.GoalsView
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import kotlinx.coroutines.Dispatchers
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
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/** The Goals screen over a real replica: periods, progress, the rings, filtering, the lit chain, the quick log, copying and the list view. */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class GoalsViewModelTest {
    private lateinit var test: TestReplica
    private lateinit var goals: GoalList
    private lateinit var tasks: TaskList
    private lateinit var viewModel: GoalsViewModel
    private val preferences = RuntimeEnvironment.getApplication().getSharedPreferences("goals-view-test", Context.MODE_PRIVATE)

    // Saturday 19 September 2026, 01:30: with the day starting at 04:00 it is still Friday the 18th.
    private val now = LocalDateTime.parse("2026-09-19T01:30")

    @Before
    fun setUp() {
        test = TestReplica()
        preferences.edit(commit = true) { clear() }
        val rows = NewRows(test.catalog, { TestReplica.OWNER }, { Instant.parse("2026-09-18T12:00:00Z") })
        val areas = AreaList(test.replica, rows, listOf("violet"), {})
        val tags = TagList(test.replica, rows, {})
        goals = GoalList(test.replica, rows, {})
        tasks = TaskList(test.replica, rows, areas, tags, ProjectList(test.replica, rows, {}), {}) { LocalDate.parse("2026-09-18") }
        viewModel = GoalsViewModel(goals, tasks, HabitList(test.replica, rows, {}), SharedPreferencesSettingsStore(preferences), Dispatchers.Unconfined) { now }
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
    fun `each goal names the goal it feeds`() = runTest {
        val year = goals.add(GoalDraft("Half marathon", GoalHorizon.YEAR, LocalDate.parse("2026-01-01")))!!
        val month = goals.add(GoalDraft("Run 80 km", GoalHorizon.MONTH, LocalDate.parse("2026-09-01"), GoalRules.MODE_NUMBER, parentId = year.id, target = 80.0))!!
        goals.add(GoalDraft("3 runs", GoalHorizon.WEEK, LocalDate.parse("2026-09-14"), parentId = month.id))!!

        val state = viewModel.uiState.first { it.sections.getOrNull(2)?.rows?.size == 1 }

        assertEquals("Run 80 km", state.sections[2].rows.single().parentTitle)
        assertEquals("Half marathon", state.sections[1].rows.single().parentTitle)
    }

    @Test
    fun `the rings count each horizon's goals, hits and how far along they are`() = runTest {
        val week = LocalDate.parse("2026-09-14")
        val km = goals.add(GoalDraft("Run 20 km", GoalHorizon.WEEK, week, GoalRules.MODE_NUMBER, target = 20.0, unit = "km"))!!
        goals.logAmount(km.id, LocalDate.parse("2026-09-15"), 5.0)
        val book = goals.add(GoalDraft("Book the race", GoalHorizon.WEEK, week))!!
        goals.setStatus(book.id, GoalRules.DONE)
        goals.add(GoalDraft("Call grandma", GoalHorizon.WEEK, week))!!
        goals.add(GoalDraft("Read a book", GoalHorizon.MONTH, LocalDate.parse("2026-09-01")))!!

        val state = viewModel.uiState.first { it.rings.getOrNull(2)?.rows?.size == 3 }

        assertEquals(listOf(GoalHorizon.YEAR, GoalHorizon.MONTH, GoalHorizon.WEEK, GoalHorizon.DAY), state.rings.map { it.horizon })
        val ring = state.rings[2]
        assertEquals(1 to 3, ring.hits to ring.rows.size)
        assertEquals((0.25 + 1.0 + 0.0) / 3, ring.fraction, 1e-9)
        assertEquals(0 to 1, state.rings[1].hits to state.rings[1].rows.size)
        assertEquals(0.0, state.rings[0].fraction, 0.0)
        // Friday: 5 of 20 km is behind with 4 of 7 days gone; Call grandma is still on track.
        assertEquals(1, state.behind)
        assertEquals(listOf("Run 20 km", "Call grandma", "Book the race"), ring.rows.map { it.goal.title })
        assertEquals(GoalPace.BEHIND to 7.0, ring.rows[0].standing.pace to ring.rows[0].standing.behind)
        assertEquals(5.0, ring.rows[0].quickAmount)
    }

    @Test
    fun `a ring shows only its horizon's rung, and next week only with the week`() = runTest {
        viewModel.filter(GoalHorizon.MONTH)
        viewModel.filter(GoalHorizon.DAY)

        val state = viewModel.uiState.first { it.filter == GoalHorizon.DAY }

        assertEquals(listOf(GoalHorizon.DAY), state.rungs.map { it.horizon })
        assertEquals(4, state.rings.size)
        assertEquals(null, state.nextWeek)
    }

    @Test
    fun `the week's ring keeps next week, and tapping it again shows every rung`() = runTest {
        viewModel.filter(GoalHorizon.WEEK)
        val week = viewModel.uiState.first { it.filter == GoalHorizon.WEEK }
        assertEquals(listOf(GoalHorizon.WEEK), week.rungs.map { it.horizon })
        assertEquals(LocalDate.parse("2026-09-21"), week.nextWeek?.start)

        viewModel.filter(GoalHorizon.WEEK)
        shadowOf(Looper.getMainLooper()).idle()

        val all = viewModel.uiState.first { it.filter == null }
        assertEquals(4, all.rungs.size)
    }

    @Test
    fun `picking a goal lights what it feeds and what feeds it`() = runTest {
        val year = goals.add(GoalDraft("Half marathon", GoalHorizon.YEAR, LocalDate.parse("2026-01-01")))!!
        val month = goals.add(GoalDraft("Run 80 km", GoalHorizon.MONTH, LocalDate.parse("2026-09-01"), parentId = year.id))!!
        val week = goals.add(GoalDraft("3 runs", GoalHorizon.WEEK, LocalDate.parse("2026-09-14"), parentId = month.id))!!
        goals.add(GoalDraft("Read a book", GoalHorizon.MONTH, LocalDate.parse("2026-09-01")))!!
        val day = goals.add(GoalDraft("Run 6 km", GoalHorizon.DAY, LocalDate.parse("2026-09-18"), parentId = week.id))!!

        viewModel.pick(month.id)
        val state = viewModel.uiState.first { it.picked != null }

        assertEquals("Run 80 km", state.picked?.goal?.title)
        assertEquals(setOf(year.id, month.id, week.id, day.id), state.chain)
    }

    @Test
    fun `picking the same goal again, or Clear, puts the chain out`() = runTest {
        val goal = goals.add(GoalDraft("3 runs", GoalHorizon.WEEK, LocalDate.parse("2026-09-14")))!!
        val other = goals.add(GoalDraft("Read", GoalHorizon.WEEK, LocalDate.parse("2026-09-14")))!!
        viewModel.pick(goal.id)
        viewModel.pick(goal.id)
        viewModel.pick(other.id)
        viewModel.clearPick()
        viewModel.pick("not-a-goal")

        val state = viewModel.uiState.first { it.sections.getOrNull(2)?.rows?.size == 2 }

        assertEquals(null, state.picked)
        assertEquals(emptySet<String>(), state.chain)
    }

    @Test
    fun `the quick log repeats the latest amount and ticks a done-or-not goal`() = runTest {
        val week = LocalDate.parse("2026-09-14")
        val km = goals.add(GoalDraft("Run 20 km", GoalHorizon.WEEK, week, GoalRules.MODE_NUMBER, target = 20.0, unit = "km"))!!
        val fresh = goals.add(GoalDraft("Swim 2 km", GoalHorizon.WEEK, week, GoalRules.MODE_NUMBER, target = 2.0, unit = "km"))!!
        val call = goals.add(GoalDraft("Call grandma", GoalHorizon.WEEK, week))!!
        goals.logAmount(km.id, week, 7.5)

        assertTrue(viewModel.quickLog(GoalRow(km, GoalProgress(7.5, 20.0, 0.375, false), quickAmount = 7.5)))
        assertFalse(viewModel.quickLog(GoalRow(fresh, GoalProgress(0.0, 2.0, 0.0, false))))
        assertTrue(viewModel.quickLog(GoalRow(call, GoalProgress(0.0, 1.0, 0.0, false))))

        val state = viewModel.uiState.first { it.hits == setOf(call.id) }
        val rows = state.sections[2].rows.associateBy { it.goal.title }
        assertEquals(15.0, rows.getValue("Run 20 km").progress.value, 0.0)
        assertEquals(0.0, rows.getValue("Swim 2 km").progress.value, 0.0)
        assertEquals(setOf(week, LocalDate.parse("2026-09-18")), goals.entries().map { it.day }.toSet())
    }

    @Test
    fun `next week offers this week's goals, and copying brings them over`() = runTest {
        val week = LocalDate.parse("2026-09-14")
        val month = goals.add(GoalDraft("Run 80 km", GoalHorizon.MONTH, LocalDate.parse("2026-09-01")))!!
        goals.add(GoalDraft("3 runs", GoalHorizon.WEEK, week, parentId = month.id))!!
        val before = viewModel.uiState.first { it.sections.getOrNull(2)?.rows?.size == 1 }
        val next = before.nextWeek!!
        assertTrue(next.canCopy)
        assertTrue(next.rows.isEmpty())

        viewModel.copyPrevious(next)
        shadowOf(Looper.getMainLooper()).idle()

        val after = viewModel.uiState.first { it.nextWeek?.rows?.isNotEmpty() == true }
        val copied = after.nextWeek!!.rows.single()
        assertEquals("3 runs" to LocalDate.parse("2026-09-21"), copied.goal.title to copied.goal.periodStart)
        // Next week still overlaps September, so it keeps feeding the month.
        assertEquals(month.id, copied.goal.parentId)
        assertFalse(after.nextWeek!!.canCopy)
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

    @Test
    fun `the goals show as the ladder at first`() = runTest {
        val state = viewModel.uiState.first { it.loaded }

        assertEquals(GoalsView.LADDER, state.view)
    }

    @Test
    fun `the list view is remembered on the device, and puts a lit chain out`() = runTest {
        val goal = goals.add(GoalDraft("3 runs", GoalHorizon.WEEK, LocalDate.parse("2026-09-14")))!!
        viewModel.pick(goal.id)

        viewModel.showView(GoalsView.LIST)
        shadowOf(Looper.getMainLooper()).idle()

        val state = viewModel.uiState.first { it.view == GoalsView.LIST && it.sections.getOrNull(2)?.rows?.size == 1 }
        assertEquals(null, state.picked)
        assertEquals(emptySet<String>(), state.chain)
        // What the next start of the app reads back.
        assertEquals(GoalsView.LIST, SharedPreferencesSettingsStore(preferences).goalsView.value)
    }

    @Test
    fun `the list groups every period in order, the goals that need you first, whatever the rings show`() = runTest {
        val week = LocalDate.parse("2026-09-14")
        goals.add(GoalDraft("Half marathon", GoalHorizon.YEAR, LocalDate.parse("2026-01-01")))!!
        goals.add(GoalDraft("Read a book", GoalHorizon.MONTH, LocalDate.parse("2026-09-01")))!!
        val book = goals.add(GoalDraft("Book the race", GoalHorizon.WEEK, week))!!
        goals.setStatus(book.id, GoalRules.DONE)
        goals.add(GoalDraft("Call grandma", GoalHorizon.WEEK, week))!!
        val km = goals.add(GoalDraft("Run 20 km", GoalHorizon.WEEK, week, GoalRules.MODE_NUMBER, target = 20.0, unit = "km"))!!
        goals.logAmount(km.id, LocalDate.parse("2026-09-15"), 5.0)
        goals.add(GoalDraft("Inbox zero", GoalHorizon.DAY, LocalDate.parse("2026-09-18")))!!
        goals.add(GoalDraft("Plan the trip", GoalHorizon.WEEK, LocalDate.parse("2026-09-21")))!!
        viewModel.filter(GoalHorizon.DAY)
        viewModel.showView(GoalsView.LIST)
        shadowOf(Looper.getMainLooper()).idle()

        val state = viewModel.uiState.first { it.view == GoalsView.LIST && it.sections.sumOf { section -> section.rows.size } == 7 }

        assertEquals(
            listOf(
                GoalHorizon.YEAR to "2026-01-01",
                GoalHorizon.MONTH to "2026-09-01",
                GoalHorizon.WEEK to "2026-09-14",
                GoalHorizon.DAY to "2026-09-18",
                GoalHorizon.WEEK to "2026-09-21",
            ),
            state.groups.map { it.horizon to it.start.toString() },
        )
        assertEquals(
            listOf(listOf("Half marathon"), listOf("Read a book"), listOf("Run 20 km", "Call grandma", "Book the race"), listOf("Inbox zero"), listOf("Plan the trip")),
            state.groups.map { group -> group.rows.map { it.goal.title } },
        )
        assertEquals(GoalPace.BEHIND, state.groups[2].rows[0].standing.pace)
    }
}
