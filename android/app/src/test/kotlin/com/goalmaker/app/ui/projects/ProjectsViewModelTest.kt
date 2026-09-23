package com.goalmaker.app.ui.projects

import android.app.Application
import com.goalmaker.app.application.planning.AreaList
import com.goalmaker.app.application.planning.NewRows
import com.goalmaker.app.application.planning.ProjectDraft
import com.goalmaker.app.application.planning.ProjectList
import com.goalmaker.app.application.planning.ProjectRules
import com.goalmaker.app.application.planning.TagList
import com.goalmaker.app.application.planning.TaskList
import com.goalmaker.app.application.planning.TaskState
import com.goalmaker.app.data.replica.TestReplica
import java.time.Instant
import java.time.LocalDate
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
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

/** The Projects screen's who-made-it switch and new items over a real replica (docs/projects.md). */
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

    @Test
    fun `a new item keeps the column, priority and notes it was given`() = runTest {
        val project = projects.add(ProjectDraft("GoalMaker"))!!
        viewModel.uiState.first { it.selected?.id == project.id }

        viewModel.addItem("Undo on the board", ProjectRules.IDEA, ProjectRules.DOING, ProjectRules.HIGH, "Like the lists have")

        val item = tasks.all().single()
        assertEquals(project.id, item.projectId)
        assertEquals(ProjectRules.IDEA, item.itemType)
        assertEquals(ProjectRules.DOING, item.boardColumn)
        assertEquals(ProjectRules.HIGH, item.priority)
        assertEquals("Like the lists have", item.notes)
    }

    @Test
    fun `undoing a move to Done puts the item back in its column, open`() = runTest {
        val project = projects.add(ProjectDraft("GoalMaker"))!!
        val added = tasks.add("Ship the board")!!
        tasks.setProject(added.id, project.id, ProjectRules.TASK)
        tasks.setBoardColumn(added.id, ProjectRules.DOING)
        val event = async(start = CoroutineStart.UNDISPATCHED) { viewModel.undo.first() }

        viewModel.move(tasks.find(added.id)!!, ProjectRules.DONE)
        assertEquals(TaskState.DONE, tasks.find(added.id)!!.state)
        event.await().undo()

        val item = tasks.find(added.id)!!
        assertEquals(ProjectRules.DOING, item.boardColumn)
        assertEquals(TaskState.OPEN, item.state)
    }

    @Test
    fun `undoing taking an item out puts it back in the project where it was`() = runTest {
        val project = projects.add(ProjectDraft("GoalMaker"))!!
        val milestone = projects.addMilestone(project.id, "M1")!!
        val added = tasks.add("Cache the release feed")!!
        tasks.setProject(added.id, project.id, ProjectRules.BUG)
        tasks.setBoardColumn(added.id, ProjectRules.DOING)
        tasks.setMilestone(added.id, milestone.id)
        val event = async(start = CoroutineStart.UNDISPATCHED) { viewModel.undo.first() }

        viewModel.removeFromProject(tasks.find(added.id)!!)
        assertEquals(null, tasks.find(added.id)!!.projectId)
        event.await().undo()

        val item = tasks.find(added.id)!!
        assertEquals(project.id, item.projectId)
        assertEquals(ProjectRules.BUG, item.itemType)
        assertEquals(ProjectRules.DOING, item.boardColumn)
        assertEquals(milestone.id, item.milestoneId)
    }
}
