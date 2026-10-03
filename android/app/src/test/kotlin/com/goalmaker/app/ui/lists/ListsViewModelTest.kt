package com.goalmaker.app.ui.lists

import android.app.Application
import android.content.Context
import android.os.Looper
import androidx.core.content.edit
import com.goalmaker.app.application.planning.AreaList
import com.goalmaker.app.application.planning.GoalList
import com.goalmaker.app.application.planning.HabitDraft
import com.goalmaker.app.application.planning.HabitList
import com.goalmaker.app.application.planning.HabitRules
import com.goalmaker.app.application.planning.HabitStanding
import com.goalmaker.app.application.planning.NewRows
import com.goalmaker.app.application.planning.ProjectDraft
import com.goalmaker.app.application.planning.ProjectList
import com.goalmaker.app.application.planning.ProjectRules
import com.goalmaker.app.application.planning.ReminderList
import com.goalmaker.app.application.planning.ReminderScheduler
import com.goalmaker.app.application.planning.ReminderService
import com.goalmaker.app.application.planning.TagList
import com.goalmaker.app.application.planning.TaskList
import com.goalmaker.app.application.sync.FakeServer
import com.goalmaker.app.application.sync.SyncCoordinator
import com.goalmaker.app.application.sync.SyncEngine
import com.goalmaker.app.data.replica.TestReplica
import com.goalmaker.app.data.settings.SharedPreferencesSettingsStore
import com.goalmaker.app.domain.composer.ComposerDraft
import com.goalmaker.app.domain.planning.QuietHours
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
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

/**
 * Today's habits half on the phone (the habits redesign): only habits on Today, the switch, Hide done,
 * what is left, the all done card, and checking in and skipping from a card. Each test sets everything
 * up first and waits on the state once (docs/pitfalls.md).
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class ListsViewModelTest {
    private lateinit var test: TestReplica
    private lateinit var habits: HabitList
    private lateinit var tasks: TaskList
    private lateinit var areas: AreaList
    private lateinit var projects: ProjectList
    private lateinit var viewModel: ListsViewModel
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)

    // Friday 18 September 2026, noon.
    private val now = LocalDateTime.parse("2026-09-18T12:00")
    private val today = LocalDate.parse("2026-09-18")

    @Before
    fun setUp() {
        test = TestReplica()
        val rows = NewRows(test.catalog, { TestReplica.OWNER }, { Instant.parse("2026-09-18T10:00:00Z") })
        areas = AreaList(test.replica, rows, listOf("violet"), {})
        val tags = TagList(test.replica, rows, {})
        projects = ProjectList(test.replica, rows, {})
        tasks = TaskList(test.replica, rows, areas, tags, projects, {}) { today }
        habits = HabitList(test.replica, rows, {})
        val preferences = RuntimeEnvironment.getApplication().getSharedPreferences("lists-test", Context.MODE_PRIVATE)
        preferences.edit(commit = true) { clear() }
        val settings = SharedPreferencesSettingsStore(preferences)
        val reminders = ReminderService(
            reminders = ReminderList(test.replica, rows, {}),
            tasks = tasks,
            scheduler = object : ReminderScheduler {
                override fun armAt(at: LocalDateTime) = Unit

                override fun cancel() = Unit
            },
            quietHours = { QuietHours.OFF },
            dayStartHour = { 4 },
            now = { now },
            remindedUntil = { null },
            setRemindedUntil = {},
        )
        val sync = SyncCoordinator(
            engine = SyncEngine(test.catalog, test.replica, FakeServer()) { Instant.parse("2026-09-18T10:00:00Z") },
            replica = test.replica,
            scope = scope,
            io = Dispatchers.Unconfined,
            now = { Instant.parse("2026-09-18T10:00:00Z") },
            debounce = 2.seconds,
        )
        viewModel = ListsViewModel(tasks, areas, tags, projects, GoalList(test.replica, rows, {}), habits, settings, reminders, sync, Dispatchers.Unconfined) { now }
    }

    @After
    fun tearDown() {
        scope.cancel()
        test.close()
    }

    @Test
    fun `an area filter keeps a project item without an area when its project is in the area`() = runTest {
        val work = areas.findOrCreate("Work")!!
        val project = projects.add(ProjectDraft("GoalMaker", areaId = work.id))!!
        val item = tasks.add(ComposerDraft(title = "Ship the board", plannedDate = today))!!
        tasks.setProject(item.id, project.id, ProjectRules.TASK)
        tasks.add(ComposerDraft(title = "Water plants", plannedDate = today))

        viewModel.filterByArea(work.id)

        val state = loaded { it.filter.areaId == work.id }
        assertEquals(listOf("Ship the board"), state.lists!!.todaySections.more.map { it.title })
    }

    @Test
    fun `only the habits on Today show, and Today starts on its tasks`() = runTest {
        habits.add(HabitDraft("Read", today))
        habits.add(HabitDraft("Floss", today, showOnToday = false))
        // Monday and Wednesday only (mask 5); the 18th is a Friday.
        habits.add(HabitDraft("Gym", today, HabitRules.WEEKDAYS, weekdays = 5))

        val state = loaded { it.habits.isNotEmpty() }
        assertEquals(listOf("Read"), state.habits.map { it.habit.name })
        assertEquals(TodaySegment.TASKS, state.segment)
        assertEquals(1, state.habitsLeft)
    }

    @Test
    fun `the switch shows the habits half and keeps it`() = runTest {
        habits.add(HabitDraft("Read", today))

        viewModel.showSegment(TodaySegment.HABITS)

        assertEquals(TodaySegment.HABITS, loaded { it.segment == TodaySegment.HABITS }.segment)
    }

    @Test
    fun `hide done hides the done habits and keeps the rest`() = runTest {
        val read = habits.add(HabitDraft("Read", today))!!
        habits.add(HabitDraft("Water", today, measure = HabitRules.COUNT, target = 8.0, unit = "glasses"))
        habits.checkIn(read.id, today)

        viewModel.setHideDoneHabits(true)

        val state = loaded { it.hideDoneHabits && it.habits.size == 2 }
        assertEquals(listOf("Water"), state.shownHabits.map { it.habit.name })
        assertEquals(1, state.habitsLeft)
        assertFalse(state.habitsAllDone)
    }

    @Test
    fun `everything done shows the all done card, and a limit never holds it up`() = runTest {
        val read = habits.add(HabitDraft("Read", today))!!
        habits.add(HabitDraft("Snacks", today, measure = HabitRules.COUNT, target = 2.0, direction = HabitRules.AT_MOST))
        habits.checkIn(read.id, today)

        val state = loaded { it.habits.size == 2 }
        assertTrue(state.habitsAllDone)
        assertEquals(0, state.habitsLeft)
        assertEquals(HabitStanding.LIMIT, state.habits.single { it.habit.name == "Snacks" }.standing)
    }

    @Test
    fun `nothing done yet shows no all done card`() = runTest {
        habits.add(HabitDraft("Snacks", today, measure = HabitRules.COUNT, target = 2.0, direction = HabitRules.AT_MOST))

        val state = loaded { it.habits.isNotEmpty() }
        assertFalse(state.habitsAllDone)
        assertEquals(0, state.habitsLeft)
    }

    @Test
    fun `a card checks in, skips and takes the skip back on the planning day`() = runTest {
        val read = habits.add(HabitDraft("Read", today))!!
        val stretch = habits.add(HabitDraft("Stretch", today))!!

        assertTrue(viewModel.tapHabit(read.id))
        viewModel.skipHabit(stretch.id, true)
        idle()
        val checkins = habits.read().checkins
        assertEquals(1.0, checkins.single { it.habitId == read.id }.value, 1e-9)
        assertTrue(checkins.single { it.habitId == stretch.id }.skipped)
        assertEquals(today, checkins.single { it.habitId == stretch.id }.day)

        viewModel.skipHabit(stretch.id, false)
        idle()
        assertFalse(habits.read().checkins.single { it.habitId == stretch.id }.skipped)
    }

    @Test
    fun `a skipped habit is neither left nor done, and stays with Hide done on`() = runTest {
        val read = habits.add(HabitDraft("Read", today))!!
        habits.skip(read.id, today)

        viewModel.setHideDoneHabits(true)

        val state = loaded { it.hideDoneHabits && it.habits.isNotEmpty() }
        assertEquals(listOf("Read"), state.shownHabits.map { it.habit.name })
        assertEquals(0, state.habitsLeft)
        assertFalse(state.habitsAllDone)
    }

    @Test
    fun `clearing a habit takes today's check-in back`() = runTest {
        val water = habits.add(HabitDraft("Water", today, measure = HabitRules.COUNT, target = 8.0))!!
        habits.checkIn(water.id, today, 3.0)

        viewModel.clearHabit(water.id)
        idle()

        assertEquals(0.0, habits.read().checkins.single().value, 1e-9)
    }

    private suspend fun loaded(until: (ListsUiState) -> Boolean): ListsUiState = viewModel.uiState.first { it.lists != null && until(it) }

    // The flows re-emit on the main looper while the state is collected.
    @Test
    fun `the new task form saves the title, day, area, top priority and notes`() {
        val home = areas.create("Home")!!

        assertTrue(viewModel.addTask("  Fix the tap ", NewTaskDay.TOMORROW, home, topPriority = true, notes = "Washer size 3/4\n"))

        val task = tasks.all().single()
        assertEquals("Fix the tap", task.title)
        assertEquals(today.plusDays(1), task.plannedDate)
        assertEquals(home.id, task.areaId)
        assertTrue(task.topPriority)
        assertEquals("Washer size 3/4", task.notes)
    }

    @Test
    fun `the new task form reads no shortcuts, and No day leaves the task in the Inbox`() {
        assertTrue(viewModel.addTask("Call mum tomorrow #family", NewTaskDay.NO_DAY, null, topPriority = false, notes = ""))

        val task = tasks.all().single()
        assertEquals("Call mum tomorrow #family", task.title)
        assertEquals(null, task.plannedDate)
        assertEquals(null, task.areaId)
        assertFalse(task.topPriority)
    }

    @Test
    fun `the new task form keeps nothing without a title, and starts on the list's day`() {
        assertFalse(viewModel.addTask("   ", NewTaskDay.TODAY, null, topPriority = false, notes = "Notes alone"))
        assertTrue(tasks.all().isEmpty())

        assertEquals(NewTaskDay.TODAY, NewTaskDay.of(ListTab.TODAY))
        assertEquals(NewTaskDay.TOMORROW, NewTaskDay.of(ListTab.TOMORROW))
        assertEquals(NewTaskDay.NO_DAY, NewTaskDay.of(ListTab.INBOX))
    }

    @Test
    fun `today on the form is the planning day`() {
        assertTrue(viewModel.addTask("Water the plants", NewTaskDay.TODAY, null, topPriority = false, notes = ""))

        assertEquals(today, tasks.all().single().plannedDate)
    }

    private fun idle() = shadowOf(Looper.getMainLooper()).idle()
}
