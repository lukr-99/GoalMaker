package com.goalmaker.app.ui.widget

import android.app.Application
import com.goalmaker.app.application.planning.AreaList
import com.goalmaker.app.application.planning.HabitDraft
import com.goalmaker.app.application.planning.HabitList
import com.goalmaker.app.application.planning.HabitRules
import com.goalmaker.app.application.planning.NewRows
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

    @Before
    fun setUp() {
        test = TestReplica()
        val rows = NewRows(test.catalog, { TestReplica.OWNER }, { Instant.parse("2026-09-20T12:00:00Z") })
        val areas = AreaList(test.replica, rows, listOf("violet", "blue", "cyan"), {})
        tasks = TaskList(test.replica, rows, areas, TagList(test.replica, rows, {}), {}) { today }
        habits = HabitList(test.replica, rows, {})
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
    fun `the widget holds only as many rows as it can show`() {
        repeat(WidgetContent.ROWS + 3) { index -> plan("Task $index") }

        assertEquals(WidgetContent.ROWS, WidgetContent.today(tasks.all(), today).size)
    }

    @Test
    fun `the habits widget shows what is due today and what one tap can do`() {
        habits.add(HabitDraft("Read", today.minusDays(3), emoji = "📖"))
        habits.add(HabitDraft("Water", today.minusDays(3), measure = HabitRules.COUNT, target = 8.0, unit = "glasses"))
        habits.add(HabitDraft("Run", today.minusDays(3), measure = HabitRules.AMOUNT, target = 5.0, unit = "km"))
        habits.add(HabitDraft("Old", today.minusDays(3))).also { habits.setArchived(it!!.id, true) }

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

    private fun plan(title: String, day: LocalDate = today) = tasks.add(title)!!.also { tasks.plan(it.id, day) }
}
