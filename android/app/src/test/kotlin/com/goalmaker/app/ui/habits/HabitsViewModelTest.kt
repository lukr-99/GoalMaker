package com.goalmaker.app.ui.habits

import android.app.Application
import android.os.Looper
import com.goalmaker.app.application.planning.GoalDraft
import com.goalmaker.app.application.planning.GoalHorizon
import com.goalmaker.app.application.planning.GoalList
import com.goalmaker.app.application.planning.GoalRules
import com.goalmaker.app.application.planning.HabitDraft
import com.goalmaker.app.application.planning.HabitList
import com.goalmaker.app.application.planning.HabitPeriodState
import com.goalmaker.app.application.planning.HabitRules
import com.goalmaker.app.application.planning.NewRows
import com.goalmaker.app.data.replica.TestReplica
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
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/** The Habits screen over a real replica: rings, streaks, skipping, pausing and the heatmap (M4-04). */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class HabitsViewModelTest {
    private lateinit var test: TestReplica
    private lateinit var habits: HabitList
    private lateinit var goals: GoalList
    private lateinit var viewModel: HabitsViewModel

    // Saturday 19 September 2026, 01:30: with the day starting at 04:00 it is still Friday the 18th.
    private val now = LocalDateTime.parse("2026-09-19T01:30")
    private val today = LocalDate.parse("2026-09-18")

    @Before
    fun setUp() {
        test = TestReplica()
        val rows = NewRows(test.catalog, { TestReplica.OWNER }, { Instant.parse("2026-09-18T12:00:00Z") })
        habits = HabitList(test.replica, rows, {})
        goals = GoalList(test.replica, rows, {})
        viewModel = HabitsViewModel(habits, goals, MutableStateFlow(4), Dispatchers.Unconfined) { now }
    }

    @After
    fun tearDown() = test.close()

    @Test
    fun `check-ins land on the planning day and fill the ring`() = runTest {
        val water = habits.add(HabitDraft("Water", today, measure = HabitRules.COUNT, target = 8.0, unit = "glasses"))!!

        viewModel.checkIn(water.id, 4.0)
        idle()

        val row = viewModel.uiState.first { it.loaded && it.active.isNotEmpty() }.active.single()
        assertEquals(0.5, row.ring!!, 1e-9)
        assertEquals(4.0, row.value, 1e-9)
        assertEquals(today, habits.read().checkinsOf(water.id).single().day)
    }

    @Test
    fun `a tap checks a habit and unchecks it, and an amount asks for its value`() = runTest {
        val read = habits.add(HabitDraft("Read", today))!!
        val run = habits.add(HabitDraft("Run", today, measure = HabitRules.AMOUNT, target = 5.0, unit = "km"))!!

        assertTrue(viewModel.tap(read.id))
        assertEquals(1.0, viewModel.uiState.first { it.active.any { row -> row.done } }.active.first().ring!!, 1e-9)
        assertTrue(viewModel.tap(read.id))
        assertFalse(viewModel.tap(run.id))
    }

    @Test
    fun `a streak counts the days met and a skipped day neither breaks it nor counts`() = runTest {
        val read = habits.add(HabitDraft("Read", today.minusDays(10)))!!
        habits.checkIn(read.id, today.minusDays(3))
        habits.skip(read.id, today.minusDays(2))
        habits.checkIn(read.id, today.minusDays(1))
        idle()

        val row = viewModel.uiState.first { it.loaded && it.active.isNotEmpty() }.active.single()
        assertEquals(2, row.streak)
        assertEquals(HabitPeriodState.OPEN, row.state)
        // The heatmap ends today, and the skipped day is in it.
        assertEquals(today, row.heatStart.plusDays(row.heat.size - 1L))
        assertTrue(row.heat.contains(com.goalmaker.app.application.planning.HabitHeat.Skipped))
    }

    @Test
    fun `a limit keeps the streak until the day goes over it`() = runTest {
        val snacks = habits.add(
            HabitDraft(
                "Snacks",
                today.minusDays(3),
                measure = HabitRules.COUNT,
                target = 2.0,
                unit = "snacks",
                direction = HabitRules.AT_MOST,
            ),
        )!!
        idle()

        // Three days nobody logged anything on are three days kept.
        var row = viewModel.uiState.first { it.loaded && it.active.isNotEmpty() }.active.single()
        assertEquals(3, row.streak)
        assertFalse(row.isOver)

        habits.checkIn(snacks.id, today, 2.0)
        idle()
        row = viewModel.uiState.first { it.active.single().value == 2.0 }.active.single()
        assertFalse(row.isOver)
        assertFalse(row.done)
        assertEquals(3, row.streak)

        habits.checkIn(snacks.id, today, 1.0)
        idle()
        row = viewModel.uiState.first { it.active.single().value == 3.0 }.active.single()
        assertTrue(row.isOver)
        assertEquals(0, row.streak)
        assertEquals(HabitPeriodState.MISSED, row.state)
    }

    @Test
    fun `only a day can be a limit`() = runTest {
        val weekly = habits.add(
            HabitDraft(
                "Takeaway",
                today,
                cadence = HabitRules.PER_WEEK,
                times = 2,
                measure = HabitRules.COUNT,
                target = 1.0,
                direction = HabitRules.AT_MOST,
            ),
        )!!

        assertEquals(HabitRules.AT_LEAST, weekly.direction)
    }

    @Test
    fun `a paused habit stays off today and comes back when it resumes`() = runTest {
        val read = habits.add(HabitDraft("Read", today.minusDays(5)))!!

        viewModel.pause(read.id)
        idle()
        assertTrue(viewModel.uiState.first { it.loaded && it.active.any { row -> row.paused } }.active.single().paused)
        assertTrue(HabitBoard.today(habits.read(), today).isEmpty())

        viewModel.resume(read.id)
        idle()
        assertFalse(viewModel.uiState.first { it.loaded && it.active.none { row -> row.paused } }.active.single().paused)
        assertEquals(1, HabitBoard.today(habits.read(), today).size)
    }

    @Test
    fun `today's row holds the habits due today`() = runTest {
        habits.add(HabitDraft("Read", today))
        // Friday the 18th is outside Monday, Wednesday (mask 5).
        habits.add(HabitDraft("Gym", today, HabitRules.WEEKDAYS, weekdays = 5))
        habits.add(HabitDraft("Run", today, HabitRules.PER_WEEK, times = 3))
        val old = habits.add(HabitDraft("Old", today))!!
        habits.setArchived(old.id, true)

        assertEquals(listOf("Read", "Run"), HabitBoard.today(habits.read(), today).map { it.habit.name })
    }

    @Test
    fun `a habit serving a goal shows it, and goals that are over stay choosable`() = runTest {
        val goal = goals.add(GoalDraft("Run 80 km", GoalHorizon.MONTH, today, GoalRules.MODE_NUMBER, target = 80.0, unit = "km"))!!
        val over = goals.add(GoalDraft("Last week", GoalHorizon.WEEK, today.minusWeeks(2), GoalRules.MODE_DONE))!!
        val run = habits.add(HabitDraft("Run", today, measure = HabitRules.AMOUNT, target = 5.0, unit = "km", goalId = goal.id))!!
        habits.add(HabitDraft("Read", today, goalId = over.id))
        idle()

        val state = viewModel.uiState.first { it.loaded && it.active.size == 2 }
        assertEquals("Run 80 km", state.active.first { it.habit.id == run.id }.goalTitle)
        assertTrue(state.goals.map { it.id }.containsAll(listOf(goal.id, over.id)))
    }

    @Test
    fun `saving adds a habit starting today and editing changes it`() = runTest {
        assertTrue(viewModel.save(null, HabitDraft("Read", LocalDate.MIN)))
        val added = habits.all().single()
        assertEquals(today, added.startsOn)

        assertTrue(viewModel.save(added.id, HabitDraft("Read 20 minutes", added.startsOn, measure = HabitRules.AMOUNT, target = 20.0, unit = "minutes")))
        assertFalse(viewModel.save(added.id, HabitDraft("", added.startsOn)))
        val changed = habits.all().single()
        assertEquals("Read 20 minutes", changed.name)
        assertEquals(20.0, changed.target!!, 1e-9)
    }

    @Test
    fun `an archived habit moves to its own list and a deleted one goes`() = runTest {
        val read = habits.add(HabitDraft("Read", today))!!

        viewModel.setArchived(read.id, true)
        idle()
        val archived = viewModel.uiState.first { it.loaded && it.archived.isNotEmpty() }
        assertTrue(archived.active.isEmpty())
        assertEquals("Read", archived.archived.single().habit.name)

        viewModel.delete(read.id)
        idle()
        assertTrue(viewModel.uiState.first { it.loaded && it.archived.isEmpty() }.active.isEmpty())
    }

    @Test
    fun `a milestone streak is reported once it is met`() = runTest {
        val read = habits.add(HabitDraft("Read", today.minusDays(30)))!!
        (0..6).forEach { back -> habits.checkIn(read.id, today.minusDays(back.toLong())) }
        idle()

        val state = viewModel.uiState.first { it.loaded && it.active.isNotEmpty() }
        assertEquals(7, state.active.single().streak)
        assertEquals(setOf("${read.id}:7"), state.milestones)
    }

    @Test
    fun `clearing today takes a check-in back`() = runTest {
        val water = habits.add(HabitDraft("Water", today, measure = HabitRules.COUNT, target = 8.0))!!
        viewModel.checkIn(water.id, 8.0)
        idle()
        assertEquals(1.0, viewModel.uiState.first { it.loaded && it.active.any { row -> row.done } }.active.single().ring!!, 1e-9)

        viewModel.clearToday(water.id)
        idle()
        val row = viewModel.uiState.first { it.loaded && it.active.none { row -> row.done } }.active.single()
        assertEquals(0.0, row.ring!!, 1e-9)
        assertNull(row.habit.goalId)
    }

    // The flows re-emit on the main looper while the state is collected.
    private fun idle() = shadowOf(Looper.getMainLooper()).idle()
}
