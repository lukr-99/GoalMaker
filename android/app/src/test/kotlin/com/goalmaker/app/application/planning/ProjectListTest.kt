package com.goalmaker.app.application.planning

import android.app.Application
import com.goalmaker.app.data.replica.TestReplica
import com.goalmaker.app.data.replica.with
import java.time.Instant
import java.time.LocalDate
import kotlinx.serialization.json.JsonNull
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
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
        tasks = TaskList(test.replica, rows, areas, TagList(test.replica, rows, {}), ProjectList(test.replica, rows, {}), {}) { LocalDate.parse("2026-09-20") }
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
    fun `moving an item to Dropped drops it where it sits, and moving it out reopens or finishes it`() {
        val project = projects.add(ProjectDraft("GoalMaker"))!!
        val item = tasks.add("Rewrite the sync")!!
        tasks.setProject(item.id, project.id)
        tasks.setBoardColumn(item.id, ProjectRules.DOING)

        tasks.setBoardColumn(item.id, ProjectRules.DROPPED)
        var stored = tasks.find(item.id)!!
        assertEquals(TaskState.DROPPED, stored.state)
        assertEquals(ProjectRules.DOING, stored.boardColumn)
        val board = ProjectRules.board(listOf(stored))
        assertEquals(listOf(item.id), board.single { it.column == ProjectRules.DROPPED }.items.map(TaskItem::id))
        assertTrue(board.single { it.column == ProjectRules.DOING }.items.isEmpty())

        tasks.setBoardColumn(item.id, ProjectRules.TODO)
        stored = tasks.find(item.id)!!
        assertEquals(TaskState.OPEN, stored.state)
        assertEquals(ProjectRules.TODO, stored.boardColumn)

        tasks.setBoardColumn(item.id, ProjectRules.DROPPED)
        tasks.setBoardColumn(item.id, ProjectRules.DONE)
        assertEquals(TaskState.DONE, tasks.find(item.id)!!.state)
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

        assertEquals(ProjectRules.BOARD_COLUMNS, board.map(ProjectColumn::column))
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

    @Test
    fun `a new project keeps done items 14 days, and the owner can change it or say never`() {
        val project = projects.add(ProjectDraft("GoalMaker"))!!
        assertEquals(14, project.archiveAfterDays)
        // Written into the row, since a null there would mean never rather than the server's default.
        assertEquals("14", test.replica.get("projects", project.id)!!["archive_after_days"].toString())

        assertTrue(projects.setArchiveAfterDays(project.id, 30))
        assertEquals(30, projects.get(project.id)!!.archiveAfterDays)

        assertTrue(projects.setArchiveAfterDays(project.id, null))
        assertNull(projects.get(project.id)!!.archiveAfterDays)
    }

    @Test
    fun `days outside 1 to 365 are refused`() {
        val project = projects.add(ProjectDraft("GoalMaker"))!!

        assertFalse(projects.setArchiveAfterDays(project.id, 0))
        assertFalse(projects.setArchiveAfterDays(project.id, 366))
        assertFalse(projects.setArchiveAfterDays("gone", 7))
        assertEquals(14, projects.get(project.id)!!.archiveAfterDays)
    }

    // A done item of a project, archived by hand.
    private fun archived(): String {
        val project = projects.add(ProjectDraft("GoalMaker"))!!
        val item = tasks.add("Ship the board")!!
        tasks.setProject(item.id, project.id)
        tasks.setBoardColumn(item.id, ProjectRules.DONE)
        tasks.setBoardArchived(item.id, true)
        assertEquals("2026-09-20T12:00:00.000000Z", tasks.find(item.id)!!.boardArchivedAt)
        return item.id
    }

    @Test
    fun `a done item can be archived by hand and put back`() {
        val id = archived()

        tasks.setBoardArchived(id, false)

        assertNull(tasks.find(id)!!.boardArchivedAt)
        assertEquals(TaskState.DONE, tasks.find(id)!!.state)
    }

    @Test
    fun `only a done item of a project can be archived`() {
        val project = projects.add(ProjectDraft("GoalMaker"))!!
        val open = tasks.add("Ship the board")!!
        tasks.setProject(open.id, project.id)
        val plain = tasks.add("Call the dentist")!!
        tasks.setDone(plain.id, true)

        tasks.setBoardArchived(open.id, true)
        tasks.setBoardArchived(plain.id, true)

        assertNull(tasks.find(open.id)!!.boardArchivedAt)
        assertNull(tasks.find(plain.id)!!.boardArchivedAt)
    }

    @Test
    fun `reopening an archived item brings it back in the row it queues`() {
        val id = archived()

        tasks.setDone(id, false)

        assertNull(tasks.find(id)!!.boardArchivedAt)
        assertEquals(JsonNull, test.replica.get("tasks", id)!!["board_archived_at"])
    }

    @Test
    fun `moving an archived item out of Done brings it back`() {
        val id = archived()

        tasks.setBoardColumn(id, ProjectRules.DOING)

        val item = tasks.find(id)!!
        assertEquals(TaskState.OPEN, item.state)
        assertNull(item.boardArchivedAt)
    }

    @Test
    fun `an archived item taken out of its project leaves the mark behind`() {
        val id = archived()

        tasks.setProject(id, null)

        assertNull(tasks.find(id)!!.boardArchivedAt)
    }

    @Test
    fun `dropping an archived item brings it back too`() {
        val id = archived()

        tasks.drop(id)

        assertNull(tasks.find(id)!!.boardArchivedAt)
        assertNotNull(tasks.find(id))
    }

    @Test
    fun `a project's key is kept upper-cased and blank clears it`() {
        val project = projects.add(ProjectDraft("GoalMaker"))!!
        assertNull(project.itemKey)

        assertTrue(projects.setItemKey(project.id, " gm "))
        assertEquals("GM", projects.get(project.id)!!.itemKey)

        assertTrue(projects.setItemKey(project.id, "  "))
        assertNull(projects.get(project.id)!!.itemKey)
    }

    @Test
    fun `a key that is not one is refused`() {
        val project = projects.add(ProjectDraft("GoalMaker"))!!
        projects.setItemKey(project.id, "GM")

        listOf("G", "1GM", "ABCDEFG", "G-M").forEach { key -> assertFalse(key, projects.setItemKey(project.id, key)) }

        assertEquals("GM", projects.get(project.id)!!.itemKey)
    }

    @Test
    fun `a key another project uses is refused, in any case`() {
        val first = projects.add(ProjectDraft("GoalMaker"))!!
        val second = projects.add(ProjectDraft("Game Master"))!!
        projects.setItemKey(first.id, "GM")

        assertFalse(projects.setItemKey(second.id, "gm"))
        assertTrue(projects.isKeyTaken("gm", second.id))
        assertFalse(projects.isKeyTaken("GM", first.id))
        // A project keeps its own key when it is saved again.
        assertTrue(projects.setItemKey(first.id, "GM"))
        assertEquals(listOf("GM"), projects.otherKeys(second.id))
        assertNull(projects.get(second.id)!!.itemKey)
    }

    @Test
    fun `a deleted project's key is free again`() {
        val first = projects.add(ProjectDraft("GoalMaker"))!!
        val second = projects.add(ProjectDraft("Game Master"))!!
        projects.setItemKey(first.id, "GM")
        projects.delete(first.id)

        assertTrue(projects.setItemKey(second.id, "GM"))
    }

    @Test
    fun `an item reads the number the server gave it and shows none after moving to another project`() {
        val first = projects.add(ProjectDraft("GoalMaker"))!!
        val second = projects.add(ProjectDraft("Thesis"))!!
        projects.setItemKey(first.id, "GM")
        val item = tasks.add("Give items ids")!!
        tasks.setProject(item.id, first.id)
        assertNull(tasks.find(item.id)!!.itemNumber)

        // The synced row comes back with the number the server gave it.
        test.replica.put("tasks", test.replica.get("tasks", item.id)!!.with("item_number" to 12))
        val numbered = tasks.find(item.id)!!
        assertEquals(12, numbered.itemNumber)
        assertEquals("GM-12", ProjectRules.itemIdOf(numbered, projects.get(first.id)))

        // A new key reads on the same number.
        projects.setItemKey(first.id, "GOAL")
        assertEquals("GOAL-12", ProjectRules.itemIdOf(tasks.find(item.id)!!, projects.get(first.id)))

        tasks.setProject(item.id, second.id)
        assertNull(tasks.find(item.id)!!.itemNumber)
    }
}
