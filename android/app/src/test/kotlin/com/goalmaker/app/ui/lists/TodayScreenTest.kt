package com.goalmaker.app.ui.lists

import android.app.Application
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.goalmaker.app.R
import com.goalmaker.app.application.planning.TaskState
import com.goalmaker.app.domain.composer.ComposerDraft
import com.goalmaker.app.ui.ScreenPlanner
import com.goalmaker.app.ui.ScreenTheme
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
 * Today as the owner sees it (docs/lists.md): a task planned for today is on it, ticking it off takes
 * it off the list with a Done snackbar, and the snackbar's Undo brings it back.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, qualifiers = "w411dp-h914dp")
class TodayScreenTest {
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
    fun `a task planned for today is on Today and one for tomorrow is not`() {
        planner.tasks.add(ComposerDraft(title = "Buy milk", plannedDate = planner.today))
        planner.tasks.add(ComposerDraft(title = "Pack the gym bag", plannedDate = planner.today.plusDays(1)))

        showToday()

        compose.onNodeWithText("Buy milk").assertIsDisplayed()
        compose.onNodeWithText("Pack the gym bag").assertDoesNotExist()
    }

    @Test
    fun `ticking a task off takes it off Today and Undo brings it back`() {
        val milk = planner.tasks.add(ComposerDraft(title = "Buy milk", plannedDate = planner.today))!!
        showToday()

        doneBox("Buy milk").performClick()

        compose.onNodeWithText(context.getString(R.string.lists_done_message, "Buy milk")).assertIsDisplayed()
        compose.onNodeWithText("Buy milk").assertDoesNotExist()
        assertEquals(TaskState.DONE, planner.tasks.find(milk.id)!!.state)

        compose.onNodeWithText(context.getString(R.string.lists_undo)).performClick()

        compose.onNodeWithText("Buy milk").assertIsDisplayed()
        assertEquals(TaskState.OPEN, planner.tasks.find(milk.id)!!.state)
    }

    // The checkbox beside a task's title; found by the title next to it, whatever it is called.
    // The done box says which task it ticks off ("Buy milk done").
    private fun doneBox(title: String) = compose.onNode(isToggleable() and hasContentDescription(context.getString(R.string.lists_done_box, title)))

    private fun showToday() {
        val lists = planner.lists()
        val chat = planner.chat()
        compose.setContent {
            ScreenTheme {
                ListsScreen(
                    viewModel = lists,
                    chat = chat,
                    onOpenPlan = {},
                    onOpenTask = {},
                    onOpenGoals = {},
                    onOpenHabits = {},
                    tab = ListTab.TODAY,
                    actions = {},
                )
            }
        }
    }
}
