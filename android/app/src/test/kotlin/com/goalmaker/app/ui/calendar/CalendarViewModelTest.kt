package com.goalmaker.app.ui.calendar

import android.app.Application
import android.content.Context
import android.os.Looper
import androidx.core.content.edit
import com.goalmaker.app.application.planning.AreaList
import com.goalmaker.app.application.planning.HabitDraft
import com.goalmaker.app.application.planning.HabitList
import com.goalmaker.app.application.planning.HabitStanding
import com.goalmaker.app.application.planning.NewRows
import com.goalmaker.app.application.planning.ProjectDraft
import com.goalmaker.app.application.planning.ProjectList
import com.goalmaker.app.application.planning.ProjectRules
import com.goalmaker.app.application.planning.ReminderList
import com.goalmaker.app.application.planning.TagList
import com.goalmaker.app.application.planning.TaskItem
import com.goalmaker.app.application.planning.TaskList
import com.goalmaker.app.application.planning.TaskState
import com.goalmaker.app.data.replica.TestReplica
import com.goalmaker.app.data.settings.SharedPreferencesSettingsStore
import com.goalmaker.app.domain.composer.ComposerParser
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * The calendar over a real replica (docs/calendar.md): its area and tag filter, and a project item on the
 * open day wearing its project's chip (docs/lists.md). It is Friday 18 September 2026.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class CalendarViewModelTest {
    private lateinit var test: TestReplica
    private lateinit var tasks: TaskList
    private lateinit var areas: AreaList
    private lateinit var tags: TagList
    private lateinit var projects: ProjectList
    private lateinit var habits: HabitList
    private lateinit var viewModel: CalendarViewModel
    private val now = LocalDateTime.parse("2026-09-18T12:00")
    private val today = LocalDate.parse("2026-09-18")

    @Before
    fun setUp() {
        test = TestReplica()
        val rows = NewRows(test.catalog, { TestReplica.OWNER }, { Instant.parse("2026-09-18T10:00:00Z") })
        areas = AreaList(test.replica, rows, listOf("violet"), {})
        tags = TagList(test.replica, rows, {})
        projects = ProjectList(test.replica, rows, {})
        habits = HabitList(test.replica, rows, {})
        tasks = TaskList(test.replica, rows, areas, tags, projects, {}) { today }
        val preferences = RuntimeEnvironment.getApplication().getSharedPreferences("calendar-test", Context.MODE_PRIVATE)
        preferences.edit(commit = true) { clear() }
        viewModel = CalendarViewModel(
            tasks,
            ReminderList(test.replica, rows, {}),
            areas,
            tags,
            projects,
            habits,
            SharedPreferencesSettingsStore(preferences),
            Dispatchers.Unconfined,
        ) { now }
    }

    @After
    fun tearDown() = test.close()

    @Test
    fun `a project item on the open day names its project`() = runTest {
        planToday("Fix the build +GoalMaker")
        planToday("Buy milk")
        viewModel.open(today)

        val state = viewModel.uiState.first { it.loaded && it.openDay?.planned?.size == 2 }
        val planned = state.openDay!!.planned
        assertEquals("GoalMaker", state.projectOf(planned.single { it.title == "Fix the build" })?.name)
        assertNull(state.projectOf(planned.single { it.title == "Buy milk" }))
    }

    @Test
    fun `an item of a deleted project names none`() = runTest {
        planToday("Fix the build +GoalMaker")
        projects.delete(projects.find("GoalMaker")!!.id)
        viewModel.open(today)

        val state = viewModel.uiState.first { it.loaded && it.openDay?.planned?.size == 1 }
        assertNull(state.projectOf(state.openDay!!.planned.single()))
    }

    private fun planToday(line: String) {
        val task = tasks.add(ComposerParser.parse(line, now))!!
        tasks.plan(task.id, today)
    }

    // Lets the view model's collectors, which run on the main looper, catch up with a change.
    private fun settle() = shadowOf(Looper.getMainLooper()).idle()

    private fun planned(state: CalendarUiState): List<String> = state.days.single { it.day == today }.planned.map(TaskItem::title)

    @Test
    fun `an area narrows the day, a project item without an area taking its project's`() = runTest {
        tasks.add(ComposerParser.parse("Fix the shelf @Home", now).copy(plannedDate = today))
        tasks.add(ComposerParser.parse("Send the invoice @Work", now).copy(plannedDate = today))
        val work = areas.all().single { it.name == "Work" }
        val project = projects.add(ProjectDraft("GoalMaker", areaId = work.id))!!
        val item = tasks.add(ComposerParser.parse("Ship the board", now).copy(plannedDate = today))!!
        tasks.setProject(item.id, project.id, ProjectRules.TASK)

        assertEquals(3, viewModel.uiState.first { it.loaded }.days.single { it.day == today }.count)

        viewModel.filterByArea(work.id)
        settle()
        val state = viewModel.uiState.first { it.filter.filter.areaId == work.id }
        assertEquals(listOf("Send the invoice", "Ship the board"), planned(state).sorted())
        assertEquals(2, state.days.single { it.day == today }.count)
    }

    @Test
    fun `a tag narrows the deadlines too`() = runTest {
        val stamps = tasks.add(ComposerParser.parse("Buy stamps #errand", now))!!
        val report = tasks.add(ComposerParser.parse("Write the report", now))!!
        tasks.setDeadline(stamps.id, today)
        tasks.setDeadline(report.id, today)
        val errand = tags.all().single { it.name == "errand" }

        viewModel.filterByTag(errand.id)
        val state = viewModel.uiState.first { it.filter.filter.tagId == errand.id }

        assertEquals(listOf("Buy stamps"), state.days.single { it.day == today }.deadlines.map(TaskItem::title))
    }

    @Test
    fun `a day gone by lists its habits, and a check-in or a fail lands on that day`() = runTest {
        val read = habits.add(HabitDraft("Read", LocalDate.parse("2026-09-01")))!!
        val floss = habits.add(HabitDraft("Floss", LocalDate.parse("2026-09-01")))!!
        val monday = LocalDate.parse("2026-09-14")
        viewModel.open(monday)

        assertEquals(listOf("Read", "Floss"), viewModel.uiState.first { it.dayHabits.size == 2 }.dayHabits.map { it.habit.name })
        assertEquals(true, viewModel.tapHabit(read.id, monday))
        viewModel.failHabit(floss.id, monday, true)
        settle()

        val rows = viewModel.uiState.first { state -> state.dayHabits.none { it.standing == HabitStanding.LEFT } }.dayHabits
        assertEquals(HabitStanding.DONE, rows.single { it.habit.name == "Read" }.standing)
        assertEquals(HabitStanding.FAILED, rows.single { it.habit.name == "Floss" }.standing)
        assertEquals(listOf(monday, monday), habits.read().checkins.map { it.day })
    }

    @Test
    fun `a day to come has no habits to check in`() = runTest {
        habits.add(HabitDraft("Read", LocalDate.parse("2026-09-01")))
        viewModel.open(today.plusDays(2))

        assertEquals(emptyList<String>(), viewModel.uiState.first { it.loaded && it.selected != null }.dayHabits.map { it.habit.name })
    }

    @Test
    fun `a task ticked off on a day gone by is done on that day`() = runTest {
        val monday = LocalDate.parse("2026-09-14")
        val task = tasks.add(ComposerParser.parse("Pay the rent", now).copy(plannedDate = monday))!!

        viewModel.setDone(task, true, monday)
        settle()

        val done = tasks.all().single()
        assertEquals(TaskState.DONE, done.state)
        assertEquals(monday, ProjectRules.completedOn(done, java.time.ZoneId.systemDefault(), 4))
    }
}
