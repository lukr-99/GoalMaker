package com.goalmaker.app.ui.projects

import android.app.Application
import android.content.Context
import android.os.Looper
import androidx.core.content.edit
import com.goalmaker.app.application.planning.AreaList
import com.goalmaker.app.application.planning.NewRows
import com.goalmaker.app.application.planning.ProjectDraft
import com.goalmaker.app.application.planning.ProjectList
import com.goalmaker.app.application.planning.ProjectRules
import com.goalmaker.app.application.planning.TagList
import com.goalmaker.app.application.planning.TaskList
import com.goalmaker.app.application.planning.TaskState
import com.goalmaker.app.data.replica.TestReplica
import com.goalmaker.app.data.settings.SharedPreferencesSettingsStore
import com.goalmaker.app.domain.settings.BoardView
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneOffset
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
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
 * The Projects screen's who-made-it switch, new items, done items leaving the board and how the board
 * shows, over a real replica (docs/projects.md). It is noon UTC on Wednesday 30 September 2026, and the
 * day starts at 04:00.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class ProjectsViewModelTest {
    private lateinit var test: TestReplica
    private lateinit var tasks: TaskList
    private lateinit var projects: ProjectList
    private lateinit var areas: AreaList
    private lateinit var tags: TagList
    private lateinit var settings: SharedPreferencesSettingsStore
    private lateinit var viewModel: ProjectsViewModel
    private val preferences = RuntimeEnvironment.getApplication().getSharedPreferences("projects-view-test", Context.MODE_PRIVATE)

    @Before
    fun setUp() {
        test = TestReplica()
        preferences.edit(commit = true) { clear() }
        settings = SharedPreferencesSettingsStore(preferences)
        val rows = NewRows(test.catalog, { TestReplica.OWNER }, { Instant.parse("2026-09-22T17:00:00Z") })
        areas = AreaList(test.replica, rows, listOf("violet", "blue"), {})
        tags = TagList(test.replica, rows, {})
        projects = ProjectList(test.replica, rows, {})
        tasks = TaskList(test.replica, rows, areas, tags, projects, {}) { LocalDate.parse("2026-09-22") }
        viewModel = ProjectsViewModel(
            projects,
            tasks,
            areas,
            tags,
            settings,
            Dispatchers.Unconfined,
            { LocalDateTime.parse("2026-09-30T12:00") },
        ) { ZoneOffset.UTC }
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

    // A Work project with an item of its own and one filed under Home, and a Home project with an errand.
    private fun twoAreas() {
        val work = areas.findOrCreate("Work")!!
        val home = areas.findOrCreate("Home")!!
        val goalMaker = projects.add(ProjectDraft("GoalMaker", areaId = work.id))!!
        val house = projects.add(ProjectDraft("House", areaId = home.id))!!
        fun item(title: String, project: String) = tasks.add(title)!!.also { tasks.setProject(it.id, project, ProjectRules.TASK) }
        item("Ship the board", goalMaker.id)
        val shelf = item("Order a shelf for the office", goalMaker.id)
        tasks.setArea(shelf.id, home.id)
        val paint = item("Buy paint", house.id)
        tasks.setTags(paint.id, listOf("errand"))
    }

    @Test
    fun `an area keeps its projects and the items in it, an item without one taking its project's`() = runTest {
        twoAreas()
        val work = areas.all().single { it.name == "Work" }

        viewModel.filterByArea(work.id)
        val state = viewModel.uiState.first { it.filter.filter.areaId == work.id }

        assertEquals(listOf("GoalMaker"), state.projects.map { it.name })
        assertEquals(listOf("Ship the board"), todo(state))
        assertEquals(true, state.anyProject)
    }

    @Test
    fun `an item's own area keeps its project in the list under that area`() = runTest {
        twoAreas()
        val home = areas.all().single { it.name == "Home" }

        viewModel.filterByArea(home.id)
        val state = viewModel.uiState.first { it.filter.filter.areaId == home.id }

        assertEquals(listOf("GoalMaker", "House"), state.projects.map { it.name }.sorted())
        viewModel.select(state.projects.single { it.name == "GoalMaker" }.id)
        assertEquals(
            listOf("Order a shelf for the office"),
            todo(viewModel.uiState.first { it.selected?.name == "GoalMaker" && it.filter.filter.areaId == home.id }),
        )
    }

    @Test
    fun `a tag keeps the projects holding a tagged item, and clearing it shows them all`() = runTest {
        twoAreas()
        val errand = tags.all().single { it.name == "errand" }

        viewModel.filterByTag(errand.id)
        val state = viewModel.uiState.first { it.filter.filter.tagId == errand.id }
        assertEquals(listOf("House"), state.projects.map { it.name })
        assertEquals(listOf("Buy paint"), todo(state))

        viewModel.filterByTag(null)
        settle()
        assertEquals(2, viewModel.uiState.first { it.filter.filter.isEmpty && it.loaded }.projects.size)
    }

    @Test
    fun `a filter that hides every project leaves the board empty`() = runTest {
        twoAreas()
        val garden = areas.findOrCreate("Garden")!!

        viewModel.filterByArea(garden.id)
        val state = viewModel.uiState.first { it.filter.filter.areaId == garden.id }

        assertEquals(emptyList<String>(), state.projects.map { it.name })
        assertNull(state.selected)
        assertEquals(true, state.anyProject)
    }

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

    // A done item of [projectId], finished at [completedAt] as the server stamped it.
    private fun done(projectId: String, title: String, completedAt: String): String {
        val item = tasks.add(title)!!
        tasks.setProject(item.id, projectId, ProjectRules.TASK)
        tasks.setBoardColumn(item.id, ProjectRules.DONE)
        val row = test.replica.get("tasks", item.id)!!
        test.replica.put("tasks", JsonObject(row + ("completed_at" to JsonPrimitive(completedAt))))
        return item.id
    }

    // Lets the view model's collectors, which run on the main looper, catch up with a change.
    private fun settle() = shadowOf(Looper.getMainLooper()).idle()

    private fun doneTitles(state: ProjectsUiState) = state.board.single { it.column == ProjectRules.DONE }.items.map { it.title }

    @Test
    fun `done items leave the board 14 days after the day they were finished, and are counted`() = runTest {
        val project = projects.add(ProjectDraft("GoalMaker"))!!
        done(project.id, "Finished long ago", "2026-09-10T10:00:00.000000Z")
        done(project.id, "Finished on the 16th", "2026-09-16T10:00:00.000000Z")
        done(project.id, "Finished on the 17th", "2026-09-17T10:00:00.000000Z")
        tasks.setProject(tasks.add("Still to do")!!.id, project.id, ProjectRules.TASK)

        val state = viewModel.uiState.first { it.loaded && it.board.isNotEmpty() }

        assertEquals(listOf("Finished on the 17th"), doneTitles(state))
        assertEquals(listOf("Finished on the 16th", "Finished long ago"), state.archived.map { it.title })
        assertEquals(1, state.open)
    }

    @Test
    fun `the day an item was finished follows the owner's day start`() = runTest {
        val project = projects.add(ProjectDraft("GoalMaker"))!!
        // 02:00 on the 17th still belongs to the 16th while the day starts at 04:00.
        done(project.id, "Late night", "2026-09-17T02:00:00.000000Z")

        assertEquals(listOf("Late night"), viewModel.uiState.first { it.loaded && it.board.isNotEmpty() }.archived.map { it.title })

        settings.setDayStartHour(0)
        settle()

        assertEquals(listOf("Late night"), doneTitles(viewModel.uiState.first { it.archived.isEmpty() && it.board.isNotEmpty() }))
    }

    @Test
    fun `a project set to never keeps its done items, and one of its own number lets them go sooner`() = runTest {
        val project = projects.add(ProjectDraft("GoalMaker"))!!
        done(project.id, "Finished long ago", "2026-09-10T10:00:00.000000Z")
        done(project.id, "Finished on the 26th", "2026-09-26T10:00:00.000000Z")

        viewModel.updateProject(project.id, ProjectDraft("GoalMaker"), null)
        settle()
        val never = viewModel.uiState.first { it.selected?.archiveAfterDays == null && it.board.isNotEmpty() }
        assertEquals(listOf("Finished long ago", "Finished on the 26th"), doneTitles(never).sorted())
        assertEquals(emptyList<String>(), never.archived.map { it.title })

        viewModel.updateProject(project.id, ProjectDraft("GoalMaker"), 3)
        settle()
        val three = viewModel.uiState.first { it.selected?.archiveAfterDays == 3 }
        assertEquals(emptyList<String>(), doneTitles(three))
        assertEquals(2, three.archived.size)
    }

    @Test
    fun `a new project takes the number of days it was given`() = runTest {
        viewModel.addProject(ProjectDraft("GoalMaker"), 30)
        viewModel.addProject(ProjectDraft("Side project"), null)
        viewModel.addProject(ProjectDraft("Plain"))

        assertEquals(30, projects.find("GoalMaker")!!.archiveAfterDays)
        assertNull(projects.find("Side project")!!.archiveAfterDays)
        assertEquals(14, projects.find("Plain")!!.archiveAfterDays)
    }

    @Test
    fun `archiving a done item takes it off the board, and undo or put back brings it back`() = runTest {
        val project = projects.add(ProjectDraft("GoalMaker"))!!
        val id = done(project.id, "Ship the board", "2026-09-29T10:00:00.000000Z")
        viewModel.uiState.first { it.board.isNotEmpty() && doneTitles(it).isNotEmpty() }
        val event = async(start = CoroutineStart.UNDISPATCHED) { viewModel.undo.first() }

        viewModel.archive(tasks.find(id)!!)
        settle()
        val archived = viewModel.uiState.first { it.archived.isNotEmpty() }
        assertEquals(emptyList<String>(), doneTitles(archived))
        assertEquals(listOf("Ship the board"), archived.archived.map { it.title })

        event.await().undo()
        settle()
        assertEquals(listOf("Ship the board"), doneTitles(viewModel.uiState.first { it.archived.isEmpty() && it.board.isNotEmpty() }))

        viewModel.archive(tasks.find(id)!!)
        settle()
        viewModel.uiState.first { it.archived.isNotEmpty() }
        viewModel.putBack(tasks.find(id)!!)
        settle()
        assertEquals(listOf("Ship the board"), doneTitles(viewModel.uiState.first { it.archived.isEmpty() && it.board.isNotEmpty() }))
    }

    @Test
    fun `the archived items follow the who-made-it switch`() = runTest {
        val project = projects.add(ProjectDraft("GoalMaker"))!!
        val id = done(project.id, "Cache the release feed", "2026-09-01T10:00:00.000000Z")
        val row = test.replica.get("tasks", id)!!
        test.replica.put("tasks", JsonObject(row + ("made_by" to JsonPrimitive(ProjectRules.CLAUDE))))

        assertEquals(1, viewModel.uiState.first { it.loaded && it.archived.isNotEmpty() }.archived.size)

        viewModel.showMadeBy(ProjectRules.OWNER)
        settle()

        assertEquals(0, viewModel.uiState.first { it.madeBy == ProjectRules.OWNER }.archived.size)
    }

    @Test
    fun `the board shows one column at a time, and the list folds Done away, at first`() = runTest {
        projects.add(ProjectDraft("GoalMaker"))

        val state = viewModel.uiState.first { it.loaded }

        assertEquals(BoardView.COLUMNS, state.view)
        assertEquals(setOf(ProjectRules.DONE), state.collapsed)
    }

    @Test
    fun `the view and the folded sections are remembered on the device`() = runTest {
        projects.add(ProjectDraft("GoalMaker"))

        viewModel.showView(BoardView.LIST)
        viewModel.toggleColumn(ProjectRules.DONE)
        viewModel.toggleColumn(ProjectRules.BACKLOG)
        settle()

        val state = viewModel.uiState.first { it.view == BoardView.LIST && ProjectRules.BACKLOG in it.collapsed }
        assertEquals(setOf(ProjectRules.BACKLOG), state.collapsed)
        // What the next start of the app reads back.
        val again = SharedPreferencesSettingsStore(preferences)
        assertEquals(BoardView.LIST, again.boardView.value)
        assertEquals(setOf(ProjectRules.BACKLOG), again.collapsedColumns.value)
    }
}
