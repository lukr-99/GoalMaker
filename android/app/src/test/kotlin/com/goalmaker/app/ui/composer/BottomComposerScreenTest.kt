package com.goalmaker.app.ui.composer

import android.app.Application
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import com.goalmaker.app.R
import com.goalmaker.app.ui.ScreenPlanner
import com.goalmaker.app.ui.ScreenTheme
import com.goalmaker.app.ui.lists.ListTab
import com.goalmaker.app.ui.lists.ListsScreen
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
 * The bottom bar on Today (docs/composer.md): a typed line previews what it will save as chips, and
 * sending it adds the task, on Today when the line names no day, and empties the line.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, qualifiers = "w411dp-h914dp")
class BottomComposerScreenTest {
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
    fun `a typed line previews its day and a new tag as chips`() {
        showToday()

        line().performTextInput("Water plants tomorrow #home")

        compose.onNodeWithText(context.getString(R.string.composer_date_tomorrow)).assertIsDisplayed()
        compose.onNodeWithText("#home · " + context.getString(R.string.composer_new)).assertIsDisplayed()
    }

    @Test
    fun `sending a line adds the task to Today and empties the bar`() {
        showToday()

        line().performTextInput("Call the bank")
        compose.onNodeWithContentDescription(context.getString(R.string.today_add)).performClick()

        compose.onNodeWithText("Call the bank").assertIsDisplayed()
        compose.onNode(hasSetTextAction() and hasText("Call the bank")).assertDoesNotExist()
        assertEquals(listOf(planner.today), planner.tasks.all().map { it.plannedDate })
    }

    private fun line() = compose.onNode(hasSetTextAction())

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
