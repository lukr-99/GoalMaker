package com.goalmaker.app.ui.capture

import android.app.Application
import com.goalmaker.app.application.auth.AuthGateway
import com.goalmaker.app.application.auth.AuthResult
import com.goalmaker.app.application.auth.AuthSession
import com.goalmaker.app.application.planning.AreaList
import com.goalmaker.app.application.planning.ListRules
import com.goalmaker.app.application.planning.NewRows
import com.goalmaker.app.application.planning.ProjectDraft
import com.goalmaker.app.application.planning.ProjectList
import com.goalmaker.app.application.planning.ProjectRules
import com.goalmaker.app.application.planning.TagList
import com.goalmaker.app.application.planning.TaskItem
import com.goalmaker.app.application.planning.TaskList
import com.goalmaker.app.data.replica.TestReplica
import com.goalmaker.app.domain.account.EmailAddress
import com.goalmaker.app.domain.account.SignInCode
import com.goalmaker.app.domain.share.SharedCapture
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** What a share from another app saves (spec, story 10; M5-05). */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class CaptureViewModelTest {
    private val now = LocalDateTime.parse("2026-09-20T19:00")
    private lateinit var test: TestReplica
    private lateinit var tasks: TaskList
    private lateinit var projects: ProjectList
    private lateinit var viewModel: CaptureViewModel

    @Before
    fun setUp() {
        test = TestReplica()
        val rows = NewRows(test.catalog, { TestReplica.OWNER }, { Instant.parse("2026-09-20T17:00:00Z") })
        val areas = AreaList(test.replica, rows, listOf("violet", "blue"), {})
        val tags = TagList(test.replica, rows, {})
        projects = ProjectList(test.replica, rows, {})
        tasks = TaskList(test.replica, rows, areas, tags, projects, {}) { LocalDate.parse("2026-09-20") }
        viewModel = CaptureViewModel(
            tasks = tasks,
            areas = areas,
            tags = tags,
            projects = projects,
            auth = SignedIn,
            dayStartHour = MutableStateFlow(4),
            io = Dispatchers.Unconfined,
            clock = { now },
        )
    }

    @After
    fun tearDown() = test.close()

    private suspend fun save(capture: SharedCapture, line: String = capture.line) =
        viewModel.save(viewModel.preview(line), capture.notes)

    private fun only(): TaskItem = tasks.all().single()

    @Test
    fun `a shared link lands in the Inbox with its URL in the notes`() = runTest {
        val capture = SharedCapture.of("Why sleep matters", "Why sleep matters https://example.com/sleep")
        assertTrue(save(capture))

        val task = only()
        assertEquals("Why sleep matters", task.title)
        assertEquals("https://example.com/sleep", task.notes)
        assertNull(task.plannedDate)
        assertNull(task.areaId)
        assertTrue(ListRules.lists(tasks.all(), LocalDate.parse("2026-09-20")).inbox.any { it.id == task.id })
    }

    @Test
    fun `the shared text keeps its shortcuts, so one line files it`() = runTest {
        val capture = SharedCapture.of(null, "Read the release notes https://example.com/notes")
        assertTrue(save(capture, "${capture.line} @Work #reading tomorrow"))

        val task = only()
        assertEquals("Read the release notes", task.title)
        assertEquals(LocalDate.parse("2026-09-21"), task.plannedDate)
        assertNotNull(task.areaId)
        assertEquals("https://example.com/notes", task.notes)
    }

    @Test
    fun `a share into a project lands in its backlog as an idea`() = runTest {
        projects.add(ProjectDraft(name = "GoalMaker"))!!
        val capture = SharedCapture.of(null, "Cache the manifest https://example.com/issue")
        assertTrue(save(capture, "${capture.line} +GoalMaker ?"))

        val task = only()
        assertEquals(projects.find("GoalMaker")!!.id, task.projectId)
        assertEquals(ProjectRules.IDEA, task.itemType)
        assertEquals(ProjectRules.BACKLOG, task.boardColumn)
        assertEquals("https://example.com/issue", task.notes)
    }

    @Test
    fun `a project the line names but nobody made yet is made with it`() = runTest {
        assertTrue(save(SharedCapture("Ship the installer +Signing", "")))

        assertNotNull(projects.find("Signing"))
        assertEquals("Ship the installer", only().title)
        assertEquals(ProjectRules.TODO, only().boardColumn)
    }

    @Test
    fun `a quick-add line with no day waits in the Inbox`() = runTest {
        assertTrue(viewModel.save(viewModel.preview("Ask about the invoice"), notes = ""))

        val task = only()
        assertNull(task.plannedDate)
        assertNull(task.areaId)
        assertTrue(ListRules.lists(tasks.all(), LocalDate.parse("2026-09-20")).inbox.any { it.id == task.id })
    }

    @Test
    fun `the widget's Today button plans the line for today`() = runTest {
        assertTrue(viewModel.save(viewModel.preview("today Water the plants"), notes = ""))

        assertEquals(LocalDate.parse("2026-09-20"), only().plannedDate)
        assertEquals("Water the plants", only().title)
    }

    @Test
    fun `an empty share saves nothing`() = runTest {
        assertEquals(false, save(SharedCapture.of(null, "   ")))
        assertTrue(tasks.all().isEmpty())
    }

    /** Someone is signed in; the share sheet asks nothing else of the gateway. */
    private object SignedIn : AuthGateway {
        override val session: StateFlow<AuthSession> =
            MutableStateFlow(AuthSession.SignedIn(TestReplica.OWNER, "owner@example.test"))

        override suspend fun sendCode(email: EmailAddress) = AuthResult.Success

        override suspend fun verifyCode(email: EmailAddress, code: SignInCode) = AuthResult.Success

        override suspend fun signOut() = Unit
    }
}
