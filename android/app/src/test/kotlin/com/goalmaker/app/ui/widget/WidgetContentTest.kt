package com.goalmaker.app.ui.widget

import android.app.Application
import com.goalmaker.app.application.planning.AreaList
import com.goalmaker.app.application.planning.GoalDraft
import com.goalmaker.app.application.planning.GoalHorizon
import com.goalmaker.app.application.planning.GoalList
import com.goalmaker.app.application.planning.GoalRules
import com.goalmaker.app.application.planning.HabitDraft
import com.goalmaker.app.application.planning.HabitList
import com.goalmaker.app.application.planning.HabitRules
import com.goalmaker.app.application.planning.NewRows
import com.goalmaker.app.application.planning.ProjectDraft
import com.goalmaker.app.application.planning.ProjectList
import com.goalmaker.app.application.planning.TagList
import com.goalmaker.app.application.planning.TaskList
import com.goalmaker.app.data.replica.TestReplica
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** What the home screen widgets show, over a real replica (docs/widgets.md, M5-04). */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class WidgetContentTest {
    private val today = LocalDate.parse("2026-09-20")
    private lateinit var test: TestReplica
    private lateinit var tasks: TaskList
    private lateinit var habits: HabitList
    private lateinit var goals: GoalList

    @Before
    fun setUp() {
        test = TestReplica()
        val rows = NewRows(test.catalog, { TestReplica.OWNER }, { Instant.parse("2026-09-20T12:00:00Z") })
        val areas = AreaList(test.replica, rows, listOf("violet", "blue", "cyan"), {})
        tasks = TaskList(test.replica, rows, areas, TagList(test.replica, rows, {}), ProjectList(test.replica, rows, {}), {}) { today }
        habits = HabitList(test.replica, rows, {})
        goals = GoalList(test.replica, rows, {})
    }

    @After
    fun tearDown() = test.close()

    @Test
    fun `the today widget shows today's tasks in the app's order`() {
        val later = plan("Call the bank")
        tasks.schedule(later.id, today, LocalTime.parse("15:00"))
        val early = plan("Stretch")
        tasks.schedule(early.id, today, LocalTime.parse("07:00"))
        val top = plan("Ship the build")
        tasks.setTopPriority(top.id, true)
        plan("Buy milk", day = today.plusDays(1))

        val rows = WidgetContent.today(tasks.all(), today)

        assertEquals(listOf("Ship the build", "Stretch", "Call the bank"), rows.map(WidgetTask::title))
        assertTrue(rows.first().topPriority)
        assertEquals("07:00", rows[1].time)
    }

    @Test
    fun `a finished task leaves the widget and is counted in its header`() {
        val task = plan("Call the bank")
        plan("Buy milk")
        tasks.setDone(task.id, true)

        val rows = WidgetContent.today(tasks.all(), today)

        assertEquals(listOf("Buy milk"), rows.map(WidgetTask::title))
        assertEquals(1 to 2, WidgetContent.done(tasks.all(), today))
    }

    @Test
    fun `a project item on the today widget names its project while the project is there`() {
        val projects = ProjectList(test.replica, NewRows(test.catalog, { TestReplica.OWNER }, { Instant.parse("2026-09-20T12:00:00Z") }), {})
        val project = projects.add(ProjectDraft(name = "GoalMaker"))!!
        val item = plan("Fix the build")
        tasks.setProject(item.id, project.id)
        plan("Buy milk")

        val rows = WidgetContent.today(tasks.all(), today, projects = projects.all())

        assertEquals(mapOf("Fix the build" to "GoalMaker", "Buy milk" to ""), rows.associate { it.title to it.project })
        projects.delete(project.id)
        assertEquals(listOf("", ""), WidgetContent.today(tasks.all(), today, projects = projects.all()).map(WidgetTask::project))
    }

    @Test
    fun `the widget holds only as many rows as it can show`() {
        repeat(WidgetContent.ROWS + 3) { index -> plan("Task $index") }

        assertEquals(WidgetContent.ROWS, WidgetContent.today(tasks.all(), today).size)
    }

    @Test
    fun `the habits widget shows what Today shows and what one tap can do`() {
        habits.add(HabitDraft("Read", today.minusDays(3), emoji = "📖"))
        habits.add(HabitDraft("Water", today.minusDays(3), measure = HabitRules.COUNT, target = 8.0, unit = "glasses"))
        habits.add(HabitDraft("Run", today.minusDays(3), measure = HabitRules.AMOUNT, target = 5.0, unit = "km"))
        habits.add(HabitDraft("Old", today.minusDays(3))).also { habits.setArchived(it!!.id, true) }
        habits.add(HabitDraft("Floss", today.minusDays(3), showOnToday = false))
        habits.add(HabitDraft("Resting", today.minusDays(3))).also { habits.pause(it!!.id, today.minusDays(1)) }

        val rows = WidgetContent.habits(habits.read(), today)

        assertEquals(listOf("Read", "Water", "Run"), rows.map(WidgetHabit::name))
        assertEquals("📖", rows.first().emoji)
        assertTrue(rows[1].tappable)
        assertFalse("an amount asks for its value in the app", rows[2].tappable)
        assertEquals("0 of 8 glasses", rows[1].count)
        assertEquals(3, WidgetContent.habitsLeft(rows))
    }

    @Test
    fun `a checked habit is full and counts as done`() {
        val habit = habits.add(HabitDraft("Read", today.minusDays(3)))!!
        habits.tap(habit.id, today)

        val row = WidgetContent.habits(habits.read(), today).single()

        assertTrue(row.done)
        assertEquals(1.0, row.ring, 1e-9)
        assertEquals(0, WidgetContent.habitsLeft(listOf(row)))
    }

    @Test
    fun `the motivation widget shows one horizon's open goals as written lines`() {
        val week = GoalRules.periodStart(GoalHorizon.WEEK, today)
        val month = GoalRules.periodStart(GoalHorizon.MONTH, today)
        goals.add(GoalDraft("Three runs", GoalHorizon.WEEK, week, emoji = "🏃"))
        goals.add(GoalDraft("Call grandma", GoalHorizon.WEEK, week))!!.also { goals.setStatus(it.id, GoalRules.DONE) }
        goals.add(GoalDraft("Old idea", GoalHorizon.WEEK, week))!!.also { goals.setStatus(it.id, GoalRules.DROPPED) }
        goals.add(GoalDraft("Last week's", GoalHorizon.WEEK, week.minusWeeks(1)))
        goals.add(GoalDraft("Read two books", GoalHorizon.MONTH, month))
        goals.add(GoalDraft("Half marathon", GoalHorizon.YEAR, GoalRules.periodStart(GoalHorizon.YEAR, today)))

        // Done and dropped goals leave the widget; it shows what is still to reach.
        assertEquals(listOf("🏃 Three runs"), WidgetContent.goalLines(goals.all(), GoalHorizon.WEEK, today))
        assertFalse(WidgetContent.goalsAllDone(goals.all(), GoalHorizon.WEEK, today))
        assertEquals(listOf("Read two books"), WidgetContent.goalLines(goals.all(), GoalHorizon.MONTH, today))
        assertEquals(listOf("Half marathon"), WidgetContent.goalLines(goals.all(), GoalHorizon.YEAR, today))
    }

    @Test
    fun `the motivation widget has no lines when the horizon has no goals`() {
        goals.add(GoalDraft("Three runs", GoalHorizon.WEEK, GoalRules.periodStart(GoalHorizon.WEEK, today)))

        assertTrue(WidgetContent.goalLines(goals.all(), GoalHorizon.MONTH, today).isEmpty())
        assertFalse(WidgetContent.goalsAllDone(goals.all(), GoalHorizon.MONTH, today))
    }

    @Test
    fun `the motivation widget knows when every goal of the horizon is done`() {
        val month = GoalRules.periodStart(GoalHorizon.MONTH, today)
        goals.add(GoalDraft("Finish the tax return", GoalHorizon.MONTH, month))!!.also { goals.setStatus(it.id, GoalRules.DONE) }
        goals.add(GoalDraft("Old idea", GoalHorizon.MONTH, month))!!.also { goals.setStatus(it.id, GoalRules.DROPPED) }

        assertTrue(WidgetContent.goalLines(goals.all(), GoalHorizon.MONTH, today).isEmpty())
        assertTrue(WidgetContent.goalsAllDone(goals.all(), GoalHorizon.MONTH, today))
    }

    @Test
    fun `the goals widget has the four rings with how many of each are hit`() {
        val week = GoalRules.periodStart(GoalHorizon.WEEK, today)
        goals.add(GoalDraft("Three runs", GoalHorizon.WEEK, week))!!.also { goals.setStatus(it.id, GoalRules.DONE) }
        goals.add(GoalDraft("Call grandma", GoalHorizon.WEEK, week))
        goals.add(GoalDraft("Dropped", GoalHorizon.WEEK, week))!!.also { goals.setStatus(it.id, GoalRules.DROPPED) }
        goals.add(GoalDraft("Read 20 pages", GoalHorizon.DAY, today))!!.also { goals.setStatus(it.id, GoalRules.DONE) }
        val km = goals.add(
            GoalDraft("Run 80 km", GoalHorizon.MONTH, GoalRules.periodStart(GoalHorizon.MONTH, today), GoalRules.MODE_NUMBER, target = 80.0, unit = "km"),
        )!!
        goals.logAmount(km.id, today, 20.0)

        val rings = WidgetContent.rings(goals.all(), goals.entries(), tasks.all(), habits.read(), today)

        assertEquals(listOf(GoalHorizon.YEAR, GoalHorizon.MONTH, GoalHorizon.WEEK, GoalHorizon.DAY), rings.map(WidgetRing::horizon))
        assertEquals(listOf(0, 0, 1, 1), rings.map(WidgetRing::hits))
        assertEquals(listOf(0, 1, 2, 1), rings.map(WidgetRing::total))
        assertEquals(0.0, rings[0].fraction, 1e-9)
        assertEquals(0.25, rings[1].fraction, 1e-9)
        assertEquals(0.5, rings[2].fraction, 1e-9)
        assertEquals(1.0, rings[3].fraction, 1e-9)
    }

    private fun plan(title: String, day: LocalDate = today) = tasks.add(title)!!.also { tasks.plan(it.id, day) }

    @Test
    fun `the Habits widget goes to two columns only when it is wide and the habits do not fit one`() {
        // A 3 by 2 widget, about 250 by 180 dp, holds four rows one under another.
        assertEquals(1, WidgetContent.habitColumns(4, width = 250f, height = 180f))
        assertEquals(2, WidgetContent.habitColumns(5, width = 250f, height = 180f))
        assertEquals(2, WidgetContent.habitColumns(13, width = 250f, height = 400f))
        // Taller, the same habits fit one column again.
        assertEquals(1, WidgetContent.habitColumns(8, width = 250f, height = 300f))
        // Too narrow for two tiles: one column that scrolls.
        assertEquals(1, WidgetContent.habitColumns(12, width = 180f, height = 180f))
        // A widget too short for even one row still counts one, so two habits go side by side.
        assertEquals(2, WidgetContent.habitColumns(2, width = 300f, height = 40f))
    }

    @Test
    fun `the Habits widget takes every habit on Today, since it scrolls`() {
        repeat(WidgetContent.ROWS + 4) { index -> habits.add(HabitDraft("Habit $index", today.minusDays(3))) }

        assertEquals(WidgetContent.ROWS + 4, WidgetContent.habits(habits.read(), today).size)
    }
}
