package com.goalmaker.app.ui.calendar

import android.app.Application
import android.content.Context
import androidx.core.content.edit
import com.goalmaker.app.application.planning.AreaList
import com.goalmaker.app.application.planning.NewRows
import com.goalmaker.app.application.planning.ProjectList
import com.goalmaker.app.application.planning.ReminderList
import com.goalmaker.app.application.planning.TagList
import com.goalmaker.app.application.planning.TaskList
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
import org.robolectric.annotation.Config

/** The calendar's day over a real replica: a project item on it wears its project's chip (docs/lists.md). */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class CalendarViewModelTest {
    private lateinit var test: TestReplica
    private lateinit var tasks: TaskList
    private lateinit var projects: ProjectList
    private lateinit var viewModel: CalendarViewModel

    // Friday 18 September 2026, noon.
    private val now = LocalDateTime.parse("2026-09-18T12:00")
    private val today = LocalDate.parse("2026-09-18")

    @Before
    fun setUp() {
        test = TestReplica()
        val rows = NewRows(test.catalog, { TestReplica.OWNER }, { Instant.parse("2026-09-18T10:00:00Z") })
        val areas = AreaList(test.replica, rows, listOf("violet"), {})
        projects = ProjectList(test.replica, rows, {})
        tasks = TaskList(test.replica, rows, areas, TagList(test.replica, rows, {}), projects, {}) { today }
        val preferences = RuntimeEnvironment.getApplication().getSharedPreferences("calendar-test", Context.MODE_PRIVATE)
        preferences.edit(commit = true) { clear() }
        viewModel = CalendarViewModel(
            tasks,
            ReminderList(test.replica, rows, {}),
            projects,
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
}
