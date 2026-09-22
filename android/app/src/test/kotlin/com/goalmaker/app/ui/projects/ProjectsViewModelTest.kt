package com.goalmaker.app.ui.projects

import android.app.Application
import com.goalmaker.app.application.planning.AreaList
import com.goalmaker.app.application.planning.NewRows
import com.goalmaker.app.application.planning.ProjectDraft
import com.goalmaker.app.application.planning.ProjectList
import com.goalmaker.app.application.planning.ProjectRules
import com.goalmaker.app.application.planning.TagList
import com.goalmaker.app.application.planning.TaskList
import com.goalmaker.app.data.replica.TestReplica
import java.time.Instant
import java.time.LocalDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The Projects screen's who-made-it switch over a real replica (docs/projects.md). */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class ProjectsViewModelTest {
    private lateinit var test: TestReplica
    private lateinit var tasks: TaskList
    private lateinit var projects: ProjectList
    private lateinit var viewModel: ProjectsViewModel

    @Before
    fun setUp() {
        test = TestReplica()
        val rows = NewRows(test.catalog, { TestReplica.OWNER }, { Instant.parse("2026-09-22T17:00:00Z") })
        val areas = AreaList(test.replica, rows, listOf("violet", "blue"), {})
        projects = ProjectList(test.replica, rows, {})
        tasks = TaskList(test.replica, rows, areas, TagList(test.replica, rows, {}), projects, {}) { LocalDate.parse("2026-09-22") }
        viewModel = ProjectsViewModel(projects, tasks, Dispatchers.Unconfined)
    }

    @After
    fun tearDown() = test.close()

    // The owner's item and one Claude made through the connector, the way it arrives from the server.
    private fun twoItems() {
        val project = projects.add(ProjectDraft("GoalMaker"))!!
        val mine = tasks.add("Ship the board")!!
        tasks.setProject(mine.id, project.id, ProjectRules.TASK)
        val claudes = tasks.add("Cache the release feed")!!
        tasks.setProject(claudes.id, project.id, ProjectRules.TASK)
        val row = test.replica.get("tasks", claudes.id)!!
        test.replica.put("tasks", JsonObject(row + ("made_by" to JsonPrimitive(ProjectRules.CLAUDE))))
    }

    private fun todo(state: ProjectsUiState) = state.board.single { it.column == ProjectRules.TODO }.items.map { it.title }

    @Test
    fun `the board shows everyone's items at first`() = runTest {
        twoItems()

        val state = viewModel.uiState.first { it.loaded && it.board.isNotEmpty() }

        assertEquals(ProjectRules.EVERYONE, state.madeBy)
        assertEquals(listOf("Cache the release feed", "Ship the board"), todo(state).sorted())
    }

    @Test
    fun `the switch shows only the owner's items`() = runTest {
        twoItems()

        viewModel.showMadeBy(ProjectRules.OWNER)

        assertEquals(listOf("Ship the board"), todo(viewModel.uiState.first { it.madeBy == ProjectRules.OWNER && it.board.isNotEmpty() }))
    }

    @Test
    fun `the switch shows only Claude's items`() = runTest {
        twoItems()

        viewModel.showMadeBy(ProjectRules.CLAUDE)

        assertEquals(
            listOf("Cache the release feed"),
            todo(viewModel.uiState.first { it.madeBy == ProjectRules.CLAUDE && it.board.isNotEmpty() }),
        )
    }
}
