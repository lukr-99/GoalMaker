package com.goalmaker.app.ui.calendar

import android.app.Application
import android.content.Context
import android.os.Looper
import androidx.core.content.edit
import com.goalmaker.app.application.planning.AreaList
import com.goalmaker.app.application.planning.EventDraft
import com.goalmaker.app.application.planning.EventItem
import com.goalmaker.app.application.planning.EventList
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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
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
    private lateinit var events: EventList
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
        events = EventList(test.replica, rows, {})
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
            events,
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

    @Test
    fun `an event draws a bar in each week row it touches and lists on its days`() = runTest {
        // Thursday 24 to Tuesday 29 September, across the week break.
        val trip = events.add(EventDraft("Prague", LocalDate.parse("2026-09-24"), LocalDate.parse("2026-09-29")))!!

        val state = viewModel.uiState.first { it.loaded && it.bars.flatten().isNotEmpty() }

        // September's grid starts on Monday 31 August; the trip falls in its fourth and fifth rows.
        val rows = state.bars.map { row -> row.map { Triple(it.from, it.to, it.lane) } }
        assertEquals(listOf(Triple(3, 6, 0)), rows[3])
        assertEquals(listOf(Triple(0, 1, 0)), rows[4])
        assertTrue(rows.take(3).all { it.isEmpty() })
        assertEquals(listOf(trip.id), state.days.single { it.day == LocalDate.parse("2026-09-28") }.events.map(EventItem::id))
        assertEquals(emptyList<EventItem>(), state.days.single { it.day == LocalDate.parse("2026-09-30") }.events)
    }

    @Test
    fun `the area filter keeps an area's events and a tag filter hides them all`() = runTest {
        tasks.add(ComposerParser.parse("Pack @Work #errand", now))
        val work = areas.all().single { it.name == "Work" }
        events.add(EventDraft("Offsite", today, today.plusDays(1), areaId = work.id))
        events.add(EventDraft("Grandma", today))
        val errand = tags.all().single { it.name == "errand" }

        viewModel.filterByArea(work.id)
        settle()
        val byArea = viewModel.uiState.first { it.filter.filter.areaId == work.id && it.bars.flatten().isNotEmpty() }
        assertEquals(listOf("Offsite"), byArea.days.single { it.day == today }.events.map(EventItem::title))

        viewModel.filterByArea(null)
        viewModel.filterByTag(errand.id)
        settle()
        val byTag = viewModel.uiState.first { it.filter.filter.tagId == errand.id && it.filter.filter.areaId == null }
        assertEquals(emptyList<EventItem>(), byTag.days.single { it.day == today }.events)
        assertTrue(byTag.bars.flatten().isEmpty())
    }

    @Test
    fun `the event sheet saves an edit, and a delete comes back with the undo`() = runTest {
        val scope = CoroutineScope(Dispatchers.Unconfined)
        val prague = events.add(EventDraft("Prague", today, today.plusDays(2)))!!
        val undone = mutableListOf<com.goalmaker.app.ui.lists.UndoEvent>()
        scope.launch { viewModel.undo.collect { undone += it } }

        assertTrue(viewModel.saveEvent(prague, EventDraft("Prague", today.plusDays(7), today.plusDays(9), notes = "Train")))
        assertFalse(viewModel.saveEvent(prague, EventDraft(" ", today)))
        assertEquals(today.plusDays(7), events.get(prague.id)!!.startsOn)
        assertTrue(viewModel.saveEvent(null, EventDraft("Dentist", today)))
        assertEquals(listOf("Dentist", "Prague"), events.all().map(EventItem::title))

        viewModel.deleteEvent(events.get(prague.id)!!)
        settle()
        assertNull(events.get(prague.id))
        undone.single().undo()
        settle()
        assertEquals("Train", events.get(prague.id)!!.notes)
        scope.cancel()
    }
}
