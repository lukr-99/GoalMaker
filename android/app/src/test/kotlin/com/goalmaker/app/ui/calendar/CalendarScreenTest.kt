package com.goalmaker.app.ui.calendar

import android.app.Application
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasParent
import androidx.compose.ui.test.hasScrollToNodeAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import com.goalmaker.app.R
import com.goalmaker.app.application.planning.HabitDraft
import com.goalmaker.app.application.planning.TaskState
import com.goalmaker.app.domain.composer.ComposerDraft
import com.goalmaker.app.ui.ScreenPlanner
import com.goalmaker.app.ui.ScreenTheme
import java.time.LocalDate
import org.junit.After
import org.junit.Assert.assertEquals
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
 * that day.
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

    // A day of the grid by its number; the unmerged text, so a label on the cell does not hide it.
    private fun day(number: Int) = compose.onNodeWithText(number.toString(), useUnmergedTree = true)

    // The box on a task's line of the open day, found by the title on that line.
    private fun doneBox(title: String) = isToggleable() and hasParent(hasText(title))

    // The day's list sits under the grid, so it is scrolled to before it is looked at.
    private fun shown(matcher: SemanticsMatcher) = compose.onNode(hasScrollToNodeAction()).performScrollToNode(matcher).let { compose.onNode(matcher) }

    private fun showCalendar() {
        val calendar = planner.calendar()
        compose.setContent {
            ScreenTheme {
                CalendarScreen(viewModel = calendar, onOpenTask = {}, actions = {})
            }
        }
    }
}
