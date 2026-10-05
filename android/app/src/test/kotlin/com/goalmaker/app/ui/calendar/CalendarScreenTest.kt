package com.goalmaker.app.ui.calendar

import android.app.Application
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasParent
import androidx.compose.ui.test.hasScrollToNodeAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.unit.dp
import com.goalmaker.app.R
import com.goalmaker.app.application.planning.EventDraft
import com.goalmaker.app.application.planning.HabitDraft
import com.goalmaker.app.application.planning.TaskState
import com.goalmaker.app.domain.composer.ComposerDraft
import com.goalmaker.app.ui.ScreenPlanner
import com.goalmaker.app.ui.ScreenTheme
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * The calendar as the owner sees it (docs/calendar.md), on Friday 18 September 2026: picking a day
 * shows what is planned on it, and a day gone by also shows its habits and a box to tick a task off on
 * that day. The bottom bar adds to the open day, or to several days picked with a long-press.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, qualifiers = "w411dp-h914dp")
class CalendarScreenTest {
    @get:Rule
    val compose = createComposeRule()

    private lateinit var planner: ScreenPlanner
    private val context get() = RuntimeEnvironment.getApplication()

    @Before
    fun setUp() {
        planner = ScreenPlanner()
    }

    @After
    fun tearDown() = planner.close()

    @Test
    fun `picking a day shows the tasks planned on it`() {
        planner.tasks.add(ComposerDraft(title = "Dentist", plannedDate = LocalDate.parse("2026-09-22")))
        planner.tasks.add(ComposerDraft(title = "Buy milk", plannedDate = planner.today))
        showCalendar()
        compose.onNodeWithText(context.getString(R.string.calendar_pick_a_day)).assertIsDisplayed()

        day(22).performClick()

        shown(hasText("Dentist")).assertIsDisplayed()
        compose.onNodeWithText("Buy milk").assertDoesNotExist()
        // A day to come has no habits to check in yet.
        compose.onNodeWithText(context.getString(R.string.calendar_habits), ignoreCase = true).assertDoesNotExist()
    }

    @Test
    fun `a day gone by shows its habits and ticks a task off on that day`() {
        planner.habits.add(HabitDraft("Read", LocalDate.parse("2026-09-01")))
        val receipts = planner.tasks.add(ComposerDraft(title = "File the receipts", plannedDate = LocalDate.parse("2026-09-15")))!!
        showCalendar()

        day(15).performClick()

        // Section headers are in capitals.
        shown(hasText(context.getString(R.string.calendar_habits), ignoreCase = true)).assertIsDisplayed()
        shown(hasContentDescription(context.getString(R.string.habits_check_in, "Read"))).assertIsDisplayed()
        shown(doneBox("File the receipts")).assertIsOff().performClick()

        compose.onNode(doneBox("File the receipts")).assertIsOn()
        assertEquals(TaskState.DONE, planner.tasks.find(receipts.id)!!.state)
    }

    // A day of September's grid, by the date its cell says to a screen reader ("Tuesday 15 September, ...").
    private fun day(number: Int) = compose.onNode(
        hasContentDescription(DateTimeFormatter.ofPattern("EEEE d MMMM", context.resources.configuration.locales[0]).format(LocalDate.of(2026, 9, number)), substring = true),
    )

    // The box on a task's line of the open day, found by the title on that line.
    private fun doneBox(title: String) = isToggleable() and hasParent(hasText(title))

    // The day's list sits under the grid, so it is scrolled to before it is looked at.
    private fun shown(matcher: SemanticsMatcher) = compose.onNode(hasScrollToNodeAction()).performScrollToNode(matcher).let { compose.onNode(matcher) }

    private fun showCalendar() {
        val calendar = planner.calendar()
        val chat = planner.chat()
        compose.setContent {
            ScreenTheme {
                CalendarScreen(viewModel = calendar, chat = chat, onOpenTask = {}, actions = {})
            }
        }
    }

    @Test
    fun `an event across a week break draws a bar in each row, over its own days`() {
        planner.events.add(EventDraft("Prague", LocalDate.parse("2026-09-24"), LocalDate.parse("2026-09-29")))
        showCalendar()

        val bars = compose.onAllNodesWithContentDescription("Prague, 24 to 29 September")
        bars.assertCountEquals(2)
        // The first piece runs from Thursday 24 to the row's Sunday, 27.
        val piece = bars.onFirst().getUnclippedBoundsInRoot()
        val thursday = day(24).getUnclippedBoundsInRoot()
        val sunday = day(27).getUnclippedBoundsInRoot()
        assertTrue("$piece starts on Thursday $thursday", piece.left >= thursday.left - 4.dp && piece.left <= thursday.right)
        assertTrue("$piece ends on Sunday $sunday", piece.right <= sunday.right + 4.dp && piece.right >= sunday.left)
        // The cell under it counts the event for a screen reader.
        compose.onNode(hasContentDescription("Thursday 24 September, 1 event")).assertExists()
    }

    @Test
    fun `an event on the open day opens its sheet, which saves and deletes with an undo`() {
        val trip = planner.events.add(EventDraft("Prague", LocalDate.parse("2026-09-24"), LocalDate.parse("2026-09-26")))!!
        showCalendar()

        day(25).performClick()
        shown(hasText("Prague")).performClick()
        compose.onNode(hasSetTextAction() and hasText("Prague")).performTextReplacement("Prague and Brno")
        compose.onNodeWithText(context.getString(R.string.event_save)).performClick()
        compose.waitForIdle()
        assertEquals("Prague and Brno", planner.events.get(trip.id)!!.title)

        shown(hasText("Prague and Brno")).performClick()
        compose.onNodeWithText(context.getString(R.string.event_delete)).performClick()
        compose.waitForIdle()
        assertNull(planner.events.get(trip.id))
        compose.onNodeWithText(context.getString(R.string.lists_deleted_message, "Prague and Brno")).assertIsDisplayed()

        compose.onNodeWithText(context.getString(R.string.lists_undo)).performClick()
        compose.waitForIdle()
        assertEquals("Prague and Brno", planner.events.get(trip.id)!!.title)
    }

    private fun type(text: String) = compose.onNode(hasSetTextAction()).performTextInput(text)

    private fun send(label: Int) = compose.onNodeWithContentDescription(context.getString(label)).performClick()

    @Test
    fun `the bar adds a task, or with the switch an event, to the open day`() {
        showCalendar()
        day(22).performClick()

        type("Dentist")
        send(R.string.today_add)
        compose.waitForIdle()
        assertEquals(LocalDate.parse("2026-09-22"), planner.tasks.all().single().plannedDate)

        compose.onNodeWithText(context.getString(R.string.calendar_add_event)).performClick()
        type("Grandma")
        send(R.string.calendar_add_event_send)
        compose.waitForIdle()
        val grandma = planner.events.all().single()
        assertEquals(LocalDate.parse("2026-09-22") to LocalDate.parse("2026-09-22"), grandma.startsOn to grandma.endsOn)
    }

    @Test
    fun `a long-press picks several days, which add one event and then a task on each`() {
        showCalendar()
        day(23).performSemanticsAction(SemanticsActions.OnLongClick)
        day(24).performClick()
        day(25).performClick()

        // The bar counts them, and a screen reader hears that each is picked.
        compose.onNodeWithText(context.resources.getQuantityString(R.plurals.calendar_picked_days, 3, 3)).assertIsDisplayed()
        day(24).assert(hasContentDescription(context.getString(R.string.calendar_cell_picked), substring = true))
        compose.onNodeWithText(context.getString(R.string.calendar_add_one_event)).assertIsOn()

        type("Prague")
        send(R.string.calendar_add_event_send)
        compose.waitForIdle()
        val prague = planner.events.all().single()
        assertEquals(LocalDate.parse("2026-09-23") to LocalDate.parse("2026-09-25"), prague.startsOn to prague.endsOn)

        compose.onNodeWithText(context.getString(R.string.calendar_add_task_each)).performClick()
        type("Pack")
        send(R.string.today_add)
        compose.waitForIdle()
        assertEquals(
            listOf("2026-09-23", "2026-09-24", "2026-09-25").map(LocalDate::parse),
            planner.tasks.all().mapNotNull { it.plannedDate }.sorted(),
        )
        // Undo takes back all three copies.
        compose.onNodeWithText(context.getString(R.string.lists_undo)).performClick()
        compose.waitForIdle()
        assertTrue(planner.tasks.all().isEmpty())

        // Done goes back to one day.
        compose.onNodeWithText(context.getString(R.string.calendar_pick_done)).performClick()
        compose.onNodeWithText(context.resources.getQuantityString(R.plurals.calendar_picked_days, 3, 3)).assertDoesNotExist()
        assertFalse(compose.onAllNodesWithContentDescription(context.getString(R.string.calendar_cell_picked), substring = true).fetchSemanticsNodes().isNotEmpty())
    }

    @Test
    fun `a drag after the long-press picks the days it crosses`() {
        showCalendar()
        val from = day(21).getUnclippedBoundsInRoot()
        val to = day(24).getUnclippedBoundsInRoot()
        val across = Offset(((to.left + to.right) / 2 - (from.left + from.right) / 2).value, 0f)

        day(21).performTouchInput { down(center) }
        compose.mainClock.advanceTimeBy(1_000)
        day(21).performTouchInput {
            moveBy(Offset(across.x * density / 2, 0f))
            moveBy(Offset(across.x * density / 2, 0f))
            up()
        }
        compose.waitForIdle()

        compose.onNodeWithText(context.resources.getQuantityString(R.plurals.calendar_picked_days, 4, 4)).assertExists()
        listOf(21, 22, 23, 24).forEach { number ->
            day(number).assert(hasContentDescription(context.getString(R.string.calendar_cell_picked), substring = true))
        }
    }
}
