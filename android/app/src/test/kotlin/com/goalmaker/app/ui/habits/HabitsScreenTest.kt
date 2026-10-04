package com.goalmaker.app.ui.habits

import android.app.Application
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.goalmaker.app.R
import com.goalmaker.app.application.planning.HabitDraft
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
 * The Habits screen as the owner sees it (docs/habits.md): a card's button checks the habit in and then
 * offers to take it back, and the card's menu offers skipping and failing today.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, qualifiers = "w411dp-h914dp")
class HabitsScreenTest {
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
    fun `the check-in button checks the habit in and then offers to take it back`() {
        val read = planner.habits.add(HabitDraft("Read", planner.today.minusDays(7)))!!
        showHabits()

        compose.onNodeWithContentDescription(context.getString(R.string.habits_check_in, "Read")).performClick()

        compose.onNodeWithContentDescription(context.getString(R.string.habits_take_back, "Read")).assertIsDisplayed()
        compose.onNodeWithContentDescription(context.getString(R.string.habits_check_in, "Read")).assertDoesNotExist()
        assertEquals(listOf(planner.today), planner.habits.read().checkinsOf(read.id).map { it.day })
    }

    @Test
    fun `the card's menu offers skipping and failing today`() {
        planner.habits.add(HabitDraft("Read", planner.today.minusDays(7)))
        showHabits()

        compose.onNodeWithContentDescription(context.getString(R.string.habits_more, "Read")).performClick()

        compose.onNodeWithText(context.getString(R.string.habits_skip_day)).assertIsDisplayed()
        compose.onNodeWithText(context.getString(R.string.habits_fail_day)).assertIsDisplayed()
        compose.onNodeWithText(context.getString(R.string.habits_menu_check_in)).assertIsDisplayed()
    }

    private fun showHabits() {
        val habits = planner.habitsPage()
        val chat = planner.chat()
        compose.setContent {
            ScreenTheme {
                HabitsScreen(viewModel = habits, chat = chat, onBack = null)
            }
        }
    }
}
