package com.goalmaker.app.application.planning

import android.app.Application
import com.goalmaker.app.data.replica.TestReplica
import java.time.Instant
import java.time.LocalDate
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Projects, their milestones and their items on a real replica (docs/projects.md, M5-02). */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class ProjectListTest {
    private lateinit var test: TestReplica
    private lateinit var projects: ProjectList
    private lateinit var tasks: TaskList

    @Before
    fun setUp() {
        test = TestReplica()
        val rows = NewRows(test.catalog, { TestReplica.OWNER }, { Instant.parse("2026-09-20T12:00:00Z") })
        val areas = AreaList(test.replica, rows, listOf("violet", "blue", "cyan"), {})
        projects = ProjectList(test.replica, rows, {})
        tasks = TaskList(test.replica, rows, areas, TagList(test.replica, rows, {}), {}) { LocalDate.parse("2026-09-20") }
    }

    @After
    fun tearDown() = test.close()

    @Test
    fun `a project keeps where its code lives`() {
        val project = projects.add(
            ProjectDraft(
                name = "  GoalMaker  ",
                description = "The planner",
                repositoryUrl = " https://github.com/owner/goalmaker ",
                localFolder = "  ",
            ),
        )!!

        assertEquals("GoalMaker", project.name)
        assertEquals("https://github.com/owner/goalmaker", project.repositoryUrl)
        assertNull(project.localFolder)
        assertEquals(ProjectRules.ACTIVE, project.status)
        assertEquals(listOf("GoalMaker"), projects.all().map(ProjectItem::name))
    }

    @Test
    fun `a project with no name is not a project`() {
        assertNull(projects.add(ProjectDraft("   ")))
        assertTrue(projects.all().isEmpty())
    }

    @Test
    fun `an idea lands in the backlog and a task in to do`() {
        val project = projects.add(ProjectDraft("GoalMaker"))!!
        val idea = tasks.add("An idea")!!
        val work = tasks.add("Some work")!!

        tasks.setProject(idea.id, project.id, ProjectRules.IDEA)
        tasks.setProject(work.id, project.id)

        assertEquals(ProjectRules.BACKLOG, tasks.find(idea.id)!!.boardColumn)
        assertEquals(ProjectRules.IDEA, tasks.find(idea.id)!!.itemType)
        assertEquals(ProjectRules.TODO, tasks.find(work.id)!!.boardColumn)
    }

    @Test
    fun `the done column and the task's own state move together`() {
        val project = projects.add(ProjectDraft("GoalMaker"))!!
        val item = tasks.add("Ship the board")!!
        tasks.setProject(item.id, project.id)

        tasks.setBoardColumn(item.id, ProjectRules.DONE)
        assertEquals(TaskState.DONE, tasks.find(item.id)!!.state)

        tasks.setBoardColumn(item.id, ProjectRules.DOING)
        assertEquals(TaskState.OPEN, tasks.find(item.id)!!.state)

        tasks.setDone(item.id, true)
        assertEquals(ProjectRules.DONE, tasks.find(item.id)!!.boardColumn)

        tasks.setDone(item.id, false)
        assertEquals(ProjectRules.TODO, tasks.find(item.id)!!.boardColumn)
    }

    @Test
    fun `the board shows the items of one project in order`() {
        val project = projects.add(ProjectDraft("GoalMaker"))!!
        val other = projects.add(ProjectDraft("Something else"))!!
        val urgent = tasks.add("Fix the crash")!!
        val normal = tasks.add("Write the docs")!!
        val elsewhere = tasks.add("Not here")!!
        tasks.setProject(urgent.id, project.id, ProjectRules.BUG)
        tasks.setProject(normal.id, project.id)
        tasks.setProject(elsewhere.id, other.id)
        tasks.setPriority(urgent.id, ProjectRules.URGENT)

        val board = ProjectRules.board(tasks.all().filter { it.projectId == project.id })

        assertEquals(ProjectRules.COLUMNS, board.map(ProjectColumn::column))
        assertEquals(
            listOf("Fix the crash", "Write the docs"),
            board.first { it.column == ProjectRules.TODO }.items.map(TaskItem::title),
        )
    }

    @Test
    fun `a milestone belongs to its project and can be renamed`() {
        val project = projects.add(ProjectDraft("GoalMaker"))!!
        val milestone = projects.addMilestone(project.id, " M5 ")!!

        assertEquals("M5", milestone.name)
        assertEquals(listOf("M5"), projects.read().milestonesOf(project.id).map(ProjectMilestone::name))

        projects.renameMilestone(milestone.id, "M5: projects")
        assertEquals(listOf("M5: projects"), projects.read().milestonesOf(project.id).map(ProjectMilestone::name))

        projects.deleteMilestone(milestone.id)
        assertTrue(projects.read().milestonesOf(project.id).isEmpty())
    }

    @Test
    fun `a deleted project leaves its items behind`() {
        val project = projects.add(ProjectDraft("GoalMaker"))!!
        val item = tasks.add("Ship the board")!!
        tasks.setProject(item.id, project.id)

        projects.delete(project.id)

        assertTrue(projects.all().isEmpty())
        assertEquals("Ship the board", tasks.find(item.id)!!.title)
    }

    @Test
    fun `a task taken out of a project keeps nothing of the board`() {
        val project = projects.add(ProjectDraft("GoalMaker"))!!
        val item = tasks.add("Ship the board")!!
        tasks.setProject(item.id, project.id)
        val milestone = projects.addMilestone(project.id, "M5")!!
        tasks.setMilestone(item.id, milestone.id)

        tasks.setProject(item.id, null)

        val plain = tasks.find(item.id)!!
        assertNull(plain.projectId)
        assertNull(plain.boardColumn)
        assertNull(plain.milestoneId)
    }
}
