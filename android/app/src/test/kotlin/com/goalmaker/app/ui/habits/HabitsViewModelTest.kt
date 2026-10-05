package com.goalmaker.app.ui.habits

import android.app.Application
import android.os.Looper
import com.goalmaker.app.application.planning.GoalDraft
import com.goalmaker.app.application.planning.GoalHorizon
import com.goalmaker.app.application.planning.GoalList
import com.goalmaker.app.application.planning.GoalRules
import com.goalmaker.app.application.planning.HabitDot
import com.goalmaker.app.application.planning.HabitDraft
import com.goalmaker.app.application.planning.HabitGroup
import com.goalmaker.app.application.planning.HabitList
import com.goalmaker.app.application.planning.HabitPeriodState
import com.goalmaker.app.application.planning.HabitRules
import com.goalmaker.app.application.planning.HabitStanding
import com.goalmaker.app.application.planning.NewRows
import com.goalmaker.app.data.replica.TestReplica
import com.goalmaker.app.ui.composer.LineOutcome
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
    fun `a week can be a limit, and its card counts the week so far`() = runTest {
        val takeaway = habits.add(
            HabitDraft("Takeaway", today.minusDays(14), cadence = HabitRules.PER_WEEK, times = 1, direction = HabitRules.AT_MOST),
        )!!
        idle()
        var row = viewModel.uiState.first { it.loaded && it.active.isNotEmpty() }.active.single()
        assertEquals(HabitRules.AT_MOST, row.habit.direction)
        assertFalse(row.isOver)

        // Monday's is within the limit; one more on Friday (today) takes the week over.
        habits.checkIn(takeaway.id, LocalDate.parse("2026-09-14"))
        idle()
        row = viewModel.uiState.first { it.active.single().value == 1.0 }.active.single()
        assertFalse(row.isOver)

        habits.checkIn(takeaway.id, today)
        idle()
        row = viewModel.uiState.first { it.active.single().value == 2.0 }.active.single()
        assertTrue(row.isOver)
        assertEquals(HabitPeriodState.MISSED, row.state)
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
    fun `a habit kept off Today stays on the Habits page and is still due`() = runTest {
        habits.add(HabitDraft("Read", today))
        val floss = habits.add(HabitDraft("Floss", today, showOnToday = false))!!

        assertFalse(habits.find(floss.id)!!.showOnToday)
        assertEquals(listOf("Read"), HabitBoard.today(habits.read(), today).map { it.habit.name })
        assertEquals(listOf("Read", "Floss"), HabitBoard.due(habits.read(), today).map { it.habit.name })
        assertTrue(habits.tap(floss.id, today))
        assertEquals(1.0, HabitBoard.due(habits.read(), today).single { it.habit.id == floss.id }.ring)

        assertTrue(habits.update(floss.id, HabitDraft("Floss", today, showOnToday = true)))
        assertEquals(listOf("Read", "Floss"), HabitBoard.today(habits.read(), today).map { it.habit.name })
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

    @Test
    fun `habits sit in Every day, Weekly and Limits, so a limit never reads as not done`() = runTest {
        habits.add(HabitDraft("Read", today))
        habits.add(HabitDraft("Gym", today, HabitRules.WEEKDAYS, weekdays = 21))
        habits.add(HabitDraft("Run", today, HabitRules.PER_WEEK, times = 3))
        habits.add(HabitDraft("Snacks", today, measure = HabitRules.COUNT, target = 2.0, direction = HabitRules.AT_MOST))

        val state = viewModel.uiState.first { it.loaded && it.active.size == 4 }
        assertEquals(listOf(HabitGroup.DAYS, HabitGroup.WEEKLY, HabitGroup.LIMITS), state.sections.map { it.group })
        assertEquals(listOf(listOf("Read", "Gym"), listOf("Run"), listOf("Snacks")), state.sections.map { section -> section.rows.map { it.habit.name } })
        assertEquals(HabitStanding.LIMIT, state.sections.last().rows.single().standing)
    }

    @Test
    fun `hide done takes the done habits out of their groups and keeps the counts`() = runTest {
        val read = habits.add(HabitDraft("Read", today))!!
        habits.add(HabitDraft("Stretch", today))
        val run = habits.add(HabitDraft("Run", today, HabitRules.PER_WEEK, times = 3))!!
        habits.checkIn(read.id, today)
        // One run today is today's part of a weekly habit, though the week needs two more.
        habits.checkIn(run.id, today)

        viewModel.setHideDone(true)

        val state = viewModel.uiState.first { it.loaded && it.hideDone && it.active.size == 3 }
        assertEquals(listOf("Stretch"), state.sections.first { it.group == HabitGroup.DAYS }.rows.map { it.habit.name })
        assertEquals(2, state.sections.first { it.group == HabitGroup.DAYS }.total)
        val weekly = state.sections.first { it.group == HabitGroup.WEEKLY }
        assertTrue(weekly.rows.isEmpty())
        assertEquals(1, weekly.total)
    }

    @Test
    fun `the summary counts what today asks for and names the longest streak`() = runTest {
        val read = habits.add(HabitDraft("Read", today.minusDays(5), emoji = "📖"))!!
        val water = habits.add(HabitDraft("Water", today, measure = HabitRules.COUNT, target = 8.0))!!
        val stretch = habits.add(HabitDraft("Stretch", today))!!
        habits.add(HabitDraft("Snacks", today, measure = HabitRules.COUNT, target = 2.0, direction = HabitRules.AT_MOST))
        (0..4).forEach { back -> habits.checkIn(read.id, today.minusDays(back.toLong())) }
        habits.checkIn(water.id, today, 4.0)
        habits.skip(stretch.id, today)

        val state = viewModel.uiState.first { it.loaded && it.active.size == 4 }
        // Read and Water ask something of today; the limit and the skipped one don't.
        assertEquals(1, state.summary.done)
        assertEquals(2, state.summary.total)
        assertEquals(0.75, state.summary.share, 1e-9)
        assertEquals("Read", state.summary.best?.habit?.name)
        assertEquals(5, state.summary.best?.streak)
    }

    @Test
    fun `a skipped habit stays in place with Hide done on, and its week shows the skip`() = runTest {
        val read = habits.add(HabitDraft("Read", today.minusDays(10)))!!
        habits.checkIn(read.id, today.minusDays(1))
        habits.skip(read.id, today)

        viewModel.setHideDone(true)

        val row = viewModel.uiState.first { it.loaded && it.hideDone && it.active.isNotEmpty() }.sections.single().rows.single()
        assertEquals(HabitStanding.SKIPPED, row.standing)
        assertEquals(7, row.dots.size)
        assertEquals(listOf(HabitDot.MET, HabitDot.SKIPPED), row.dots.takeLast(2))
        assertEquals(HabitDot.MISSED, row.dots.first())
    }

    @Test
    fun `an archived habit leaves the groups and folds into Archived`() = runTest {
        habits.add(HabitDraft("Read", today))
        val old = habits.add(HabitDraft("Cold shower", today.minusDays(30)))!!
        habits.setArchived(old.id, true)

        val state = viewModel.uiState.first { it.loaded && it.archived.isNotEmpty() }
        assertEquals(listOf("Read"), state.sections.flatMap { it.rows }.map { it.habit.name })
        assertEquals(HabitStanding.NONE, state.archived.single().standing)
    }

    @Test
    fun `the bar adds a habit with how often and how much the line says, starting today`() = runTest {
        val outcome = viewModel.addLine("Swim 2 times a week 40 min")

        assertEquals(LineOutcome.Added, outcome)
        val swim = habits.all().single()
        assertEquals("Swim", swim.name)
        assertEquals(today, swim.startsOn)
        assertEquals(HabitRules.PER_WEEK, swim.cadence)
        assertEquals(2, swim.times)
        assertEquals(HabitRules.AMOUNT, swim.measure)
        assertEquals(40.0, swim.target!!, 1e-9)
        assertEquals("min", swim.unit)
        assertTrue(swim.showOnToday)
    }

    @Test
    fun `a counted habit on days keeps its days and its count`() = runTest {
        assertEquals(LineOutcome.Added, viewModel.addLine("Water 8 glasses every mon, wed and fri"))

        val water = habits.all().single()
        assertEquals("Water", water.name)
        assertEquals(HabitRules.WEEKDAYS, water.cadence)
        assertEquals(1 or 4 or 16, water.weekdays)
        assertEquals(HabitRules.COUNT, water.measure)
        assertEquals(8.0, water.target!!, 1e-9)
        assertEquals("glasses", water.unit)
    }

    @Test
    fun `a limit line adds a limit, a zero one included`() = runTest {
        assertEquals(LineOutcome.Added, viewModel.addLine("Coffee at most 3 cups a day"))
        assertEquals(LineOutcome.Added, viewModel.addLine("No casino this month"))

        val coffee = habits.all().single { it.name == "Coffee" }
        assertEquals(HabitRules.AT_MOST, coffee.direction)
        assertEquals(HabitRules.DAILY, coffee.cadence)
        assertEquals(HabitRules.COUNT, coffee.measure)
        assertEquals(3.0, coffee.target!!, 1e-9)
        val casino = habits.all().single { it.name == "casino" }
        assertEquals(HabitRules.AT_MOST, casino.direction)
        assertEquals(HabitRules.PER_MONTH, casino.cadence)
        assertEquals(0, casino.times)
        assertEquals(HabitRules.CHECK, casino.measure)
    }

    @Test
    fun `a line with no name left opens the habit form filled in, and adds nothing`() = runTest {
        val outcome = viewModel.addLine("3 times a week")

        assertEquals(
            LineOutcome.OpenForm(HabitDraft("", today, cadence = HabitRules.PER_WEEK, times = 3)),
            outcome,
        )
        assertTrue(habits.all().isEmpty())
    }

    @Test
    fun `the preview of a plain name is a daily check`() {
        assertEquals(HabitDraft("Read", today), viewModel.preview("Read"))
    }

    // The flows re-emit on the main looper while the state is collected.
    private fun idle() = shadowOf(Looper.getMainLooper()).idle()
}
