package com.goalmaker.app.ui.review

import android.app.Application
import android.os.Looper
import com.goalmaker.app.application.planning.AreaList
import com.goalmaker.app.application.planning.GoalDraft
import com.goalmaker.app.application.planning.GoalHorizon
import com.goalmaker.app.application.planning.GoalList
import com.goalmaker.app.application.planning.GoalRules
import com.goalmaker.app.application.planning.HabitDraft
import com.goalmaker.app.application.planning.HabitList
import com.goalmaker.app.application.planning.NewRows
import com.goalmaker.app.application.planning.ProjectList
import com.goalmaker.app.application.planning.PlanDecision
import com.goalmaker.app.application.planning.ReviewList
import com.goalmaker.app.application.planning.ReviewRules
import com.goalmaker.app.application.planning.RitualRunList
import com.goalmaker.app.application.planning.TagList
import com.goalmaker.app.application.planning.TaskList
import com.goalmaker.app.contracts.ContractFiles
import com.goalmaker.app.data.replica.TestReplica
import com.goalmaker.app.domain.composer.ComposerParser
import com.goalmaker.app.domain.planning.PromptLibrary
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/** The weekly review over a real replica: the look back, the prompts, the ratings and the next goals (M4-06). */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class ReviewViewModelTest {
    private lateinit var test: TestReplica
    private lateinit var reviews: ReviewList
    private lateinit var tasks: TaskList
    private lateinit var areas: AreaList
    private lateinit var goals: GoalList
    private lateinit var habits: HabitList
    private lateinit var rituals: RitualRunList
    private lateinit var prompts: PromptLibrary

    // Monday 21 September 2026: the week under review is 14 to 20 September.
    private val today = LocalDate.parse("2026-09-21")
    private val weekStart = LocalDate.parse("2026-09-14")

    @Before
    fun setUp() {
        test = TestReplica()
        val rows = NewRows(test.catalog, { TestReplica.OWNER }, { Instant.parse("2026-09-21T09:00:00Z") })
        areas = AreaList(test.replica, rows, listOf("violet", "blue"), {})
        val tags = TagList(test.replica, rows, {})
        tasks = TaskList(test.replica, rows, areas, tags, ProjectList(test.replica, rows, {}), {}) { today }
        goals = GoalList(test.replica, rows, {})
        habits = HabitList(test.replica, rows, {})
        reviews = ReviewList(test.replica, rows, {})
        rituals = RitualRunList(test.replica, rows, {})
        prompts = PromptLibrary.parse(File(System.getProperty("goalmaker.contracts")!!).resolve("content/prompts.json").readText())
    }

    @After
    fun tearDown() = test.close()

    private fun viewModel(kind: String = ReviewRules.WEEKLY, start: LocalDate = weekStart) = ReviewViewModel(
        kind = kind,
        periodStart = start,
        reviews = reviews,
        tasks = tasks,
        areas = areas,
        goals = goals,
        habits = habits,
        prompts = prompts,
        rituals = rituals,
        io = Dispatchers.Unconfined,
    ) { today }

    private fun done(title: String, day: LocalDate, area: String? = null) {
        val task = tasks.add(ComposerParser.parse(title, day.atTime(9, 0)))!!
        area?.let { tasks.setArea(task.id, it) }
        tasks.setDone(task.id, true)
        // The replica keeps the server's completion time; the test writes the day it happened.
        test.replica.get("tasks", task.id)?.let { row ->
            test.replica.queue(
                "tasks",
                kotlinx.serialization.json.JsonObject(
                    row + mapOf("completed_at" to kotlinx.serialization.json.JsonPrimitive("${day}T18:00:00.000000Z")),
                ),
            )
        }
    }

    @Test
    fun `the look back counts the period's tasks against the one before`() = runTest {
        done("Monday thing", weekStart)
        done("Tuesday thing", weekStart.plusDays(1))
        done("Tuesday second", weekStart.plusDays(1))
        done("Last week", weekStart.minusDays(3))
        idle()

        val state = viewModel().uiState.first { it.loaded && it.digest.done > 0 }
        assertEquals(3, state.digest.done)
        assertEquals(1, state.digest.doneBefore)
        assertEquals(2, state.digest.change)
        assertEquals(weekStart.plusDays(1), state.digest.bestDay!!.day)
        assertEquals(7, state.digest.days.size)
    }

    @Test
    fun `the area that carried the period shows up`() = runTest {
        val area = areas.create("Work")!!
        done("One", weekStart, area.id)
        done("Two", weekStart.plusDays(1), area.id)
        done("Three", weekStart.plusDays(2))
        idle()

        val state = viewModel().uiState.first { it.loaded && it.digest.strongestArea != null }
        assertEquals("Work", state.digest.strongestArea!!.name)
        assertEquals(2, state.digest.strongestArea!!.done)
    }

    @Test
    fun `the review asks prompts and keeps the answers`() = runTest {
        val model = viewModel()
        idle()
        val state = model.uiState.first { it.loaded && it.questions.isNotEmpty() }
        assertEquals(3, state.questions.size)
        assertTrue(state.questions.all { it.text.isNotBlank() && !it.text.contains("{period}") })

        model.answer(state.questions[0].promptId, "A good week.")
        model.saveAnswers()
        idle()

        val saved = reviews.find(ReviewRules.WEEKLY, weekStart)!!
        assertEquals(3, saved.reflections.size)
        assertEquals("A good week.", saved.reflections.first().answer)
        assertTrue(saved.written)
    }

    @Test
    fun `a period that calls for it gets a reactive prompt first`() = runTest {
        val goal = goals.add(GoalDraft("Run 80 km", GoalHorizon.WEEK, weekStart, GoalRules.MODE_NUMBER, target = 80.0, unit = "km"))!!
        goals.logAmount(goal.id, weekStart, 5.0)
        idle()

        val state = viewModel().uiState.first { it.loaded && it.questions.isNotEmpty() }
        assertEquals("goals/behind", state.questions.first().promptId)
        assertTrue(state.questions.first().text.contains("Run 80 km"))
    }

    @Test
    fun `mood and energy are saved and can be taken back`() = runTest {
        val model = viewModel()
        idle()
        model.uiState.first { it.loaded && it.review != null }

        model.setMood(4)
        model.setEnergy(2)
        idle()
        model.uiState.first { it.mood == 4 && it.energy == 2 }

        model.setMood(4)
        idle()
        assertEquals(null, model.uiState.first { it.mood == null }.mood)
    }

    @Test
    fun `tasks left open move to the next period, or are done or dropped`() = runTest {
        val open = tasks.add(ComposerParser.parse("Call the bank", weekStart.atTime(9, 0)))!!
        tasks.plan(open.id, weekStart.plusDays(2))
        val second = tasks.add(ComposerParser.parse("Fix the bike", weekStart.atTime(9, 0)))!!
        tasks.plan(second.id, weekStart.plusDays(3))
        idle()

        val model = viewModel()
        val state = model.uiState.first { it.loaded && it.openTasks == 2 }
        model.decide(state.digest.openTasks.first { it.title == "Call the bank" }, PlanDecision.TOMORROW)
        model.decide(state.digest.openTasks.first { it.title == "Fix the bike" }, PlanDecision.DROPPED)
        idle()

        assertEquals(today, tasks.find(open.id)!!.plannedDate)
        assertEquals(com.goalmaker.app.application.planning.TaskState.DROPPED, tasks.find(second.id)!!.state)
    }

    @Test
    fun `the last step records the ritual and the next period's goals can be copied`() = runTest {
        goals.add(GoalDraft("3 runs", GoalHorizon.WEEK, weekStart, GoalRules.MODE_DONE))
        idle()
        val model = viewModel()
        model.uiState.first { it.loaded }

        repeat(ReviewStep.entries.size) { model.next() }
        idle()

        assertTrue(today in rituals.ran(RitualRunList.WEEKLY_REVIEW))
        model.copyGoals()
        idle()
        val next = goals.all().filter { it.horizon == GoalHorizon.WEEK && it.periodStart == weekStart.plusWeeks(1) }
        assertEquals(listOf("3 runs"), next.map { it.title })
    }

    @Test
    fun `a review opened again shows the prompts it asked before`() = runTest {
        val first = viewModel()
        idle()
        val asked = first.uiState.first { it.questions.isNotEmpty() }.questions.map { it.promptId }
        first.answer(asked[1], "Something learned.")
        first.saveAnswers()
        idle()

        val again = viewModel()
        idle()
        val state = again.uiState.first { it.questions.isNotEmpty() }
        assertEquals(asked, state.questions.map { it.promptId })
        assertEquals("Something learned.", state.answers[asked[1]])
    }

    @Test
    fun `a monthly review looks at the month and its goals`() = runTest {
        val monthStart = LocalDate.parse("2026-09-01")
        goals.add(GoalDraft("Run 80 km", GoalHorizon.MONTH, monthStart, GoalRules.MODE_NUMBER, target = 80.0, unit = "km"))
        goals.add(GoalDraft("3 runs", GoalHorizon.WEEK, weekStart, GoalRules.MODE_DONE))
        habits.add(HabitDraft("Read", monthStart))
        done("Something", monthStart.plusDays(4))
        idle()

        val state = viewModel(ReviewRules.MONTHLY, monthStart).uiState.first { it.loaded && it.digest.goals.isNotEmpty() }
        assertEquals(listOf("Run 80 km"), state.digest.goals.map { it.title })
        assertEquals(LocalDate.parse("2026-09-30"), state.digest.periodEnd)
        assertEquals(1, state.digest.done)
        assertNotNull(state.digest.habits.firstOrNull { it.name == "Read" })
        assertFalse(state.questions.any { it.text.contains("{period}") })
    }

    // The flows re-emit on the main looper while the state is collected.
    private fun idle() = shadowOf(Looper.getMainLooper()).idle()
}
