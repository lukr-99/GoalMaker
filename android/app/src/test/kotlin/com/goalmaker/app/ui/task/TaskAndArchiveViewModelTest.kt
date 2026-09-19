package com.goalmaker.app.ui.task

import android.app.Application
import com.goalmaker.app.application.planning.AreaList
import com.goalmaker.app.application.planning.GoalDraft
import com.goalmaker.app.application.planning.GoalHorizon
import com.goalmaker.app.application.planning.GoalList
import com.goalmaker.app.application.planning.GoalRules
import com.goalmaker.app.application.planning.NewRows
import com.goalmaker.app.application.planning.StepList
import com.goalmaker.app.application.planning.TagItem
import com.goalmaker.app.application.planning.TagList
import com.goalmaker.app.application.planning.TaskList
import com.goalmaker.app.application.planning.TaskState
import com.goalmaker.app.data.replica.TestReplica
import com.goalmaker.app.domain.composer.ComposerParser
import com.goalmaker.app.ui.archive.ArchiveViewModel
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The detail view's tag toggling and goal picker, and the archive's search and reopen (M2-12, M4-02). */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class TaskAndArchiveViewModelTest {
    private lateinit var test: TestReplica
    private lateinit var tasks: TaskList
    private lateinit var tags: TagList
    private lateinit var areas: AreaList
    private lateinit var steps: StepList
    private lateinit var goals: GoalList

    @Before
    fun setUp() {
        test = TestReplica()
        val rows = NewRows(test.catalog, { TestReplica.OWNER }, { Instant.parse("2026-09-18T12:00:00Z") })
        areas = AreaList(test.replica, rows, listOf("violet"), {})
        tags = TagList(test.replica, rows, {})
        steps = StepList(test.replica, rows, {})
        goals = GoalList(test.replica, rows, {})
        tasks = TaskList(test.replica, rows, areas, tags, {}) { LocalDate.parse("2026-09-18") }
    }

    @After
    fun tearDown() = test.close()

    private fun taskViewModel(id: String) =
        TaskViewModel(id, tasks, areas, tags, steps, goals, Dispatchers.Unconfined) { LocalDate.parse("2026-09-18") }

    @Test
    fun `toggling a tag links it and toggling again takes it off`() {
        val task = tasks.add(ComposerParser.parse("Buy stamps #errand", LocalDateTime.parse("2026-09-18T14:00")))!!
        tags.findOrCreate("post")
        val viewModel = taskViewModel(task.id)
        val post = tags.all().first { it.name == "post" }

        viewModel.toggleTag(post)
        assertEquals(setOf("errand", "post"), tags.forTask(task.id).map(TagItem::name).toSet())

        viewModel.toggleTag(tags.all().first { it.name == "errand" })
        viewModel.addTag("#weekend")
        assertEquals(setOf("post", "weekend"), tags.forTask(task.id).map(TagItem::name).toSet())
    }

    @Test
    fun `the goal picker offers open goals still running, and linking one shows on the task`() = runTest {
        val task = tasks.add(ComposerParser.parse("Long run", LocalDateTime.parse("2026-09-18T14:00")))!!
        val week = goals.add(GoalDraft("3 runs", GoalHorizon.WEEK, LocalDate.parse("2026-09-14"), GoalRules.MODE_TASKS))!!
        goals.add(GoalDraft("Last week", GoalHorizon.WEEK, LocalDate.parse("2026-09-07")))!!
        val done = goals.add(GoalDraft("Book the race", GoalHorizon.MONTH, LocalDate.parse("2026-09-01")))!!
        goals.setStatus(done.id, GoalRules.DONE)
        val year = goals.add(GoalDraft("Half marathon", GoalHorizon.YEAR, LocalDate.parse("2026-01-01")))!!
        val viewModel = taskViewModel(task.id)

        val offered = viewModel.uiState.first { it.loaded }.goals.map { it.title }
        assertEquals(listOf("Half marathon", "3 runs"), offered)

        viewModel.setGoal(week.id)
        assertEquals(week.id, tasks.find(task.id)!!.goalId)
        viewModel.setGoal(year.id)
        viewModel.setGoal(null)
        assertEquals(null, tasks.find(task.id)!!.goalId)
    }

    @Test
    fun `the archive searches as you type and reopens a task`() = runTest {
        val bank = tasks.add(ComposerParser.parse("Call the bank", LocalDateTime.parse("2026-09-18T14:00")))!!
        val milk = tasks.add(ComposerParser.parse("Buy milk", LocalDateTime.parse("2026-09-18T14:00")))!!
        tasks.setDone(bank.id, true)
        tasks.setDone(milk.id, true)
        val archive = ArchiveViewModel(tasks, Dispatchers.Unconfined)

        archive.setQuery("bank")
        assertEquals(listOf("Call the bank"), archive.results.filterNotNull().first { it.size == 1 }.map { it.title })

        archive.reopen(bank.id)
        assertEquals(TaskState.OPEN, tasks.find(bank.id)!!.state)
    }
}
