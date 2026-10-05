package com.goalmaker.app.ui.habits

import android.app.Application
import androidx.compose.material3.Text
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.goalmaker.app.application.planning.HabitItem
import com.goalmaker.app.application.planning.HabitRules
import com.goalmaker.app.ui.TestTheme
import java.time.LocalDate
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** How often a habit runs, as its card and the bottom bar's chip say it. */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class HabitTextsTest {
    @get:Rule
    val rule = createComposeRule()

    private fun habit(cadence: String, times: Int, direction: String, measure: String = HabitRules.CHECK) = HabitItem(
        id = "h1",
        name = "Takeaway",
        startsOn = LocalDate.of(2026, 1, 1),
        cadence = cadence,
        times = times,
        measure = measure,
        target = if (measure == HabitRules.CHECK) null else 2.0,
        direction = direction,
    )

    private fun shows(habit: HabitItem, text: String) {
        rule.setContent { TestTheme { Text(cadenceText(habit)) } }
        rule.onNodeWithText(text).assertExists()
    }

    @Test
    fun `a habit to build counts the times`() =
        shows(habit(HabitRules.PER_WEEK, 3, HabitRules.AT_LEAST), "3 times a week")

    @Test
    fun `a weekly check limit counts the days it may have`() =
        shows(habit(HabitRules.PER_WEEK, 2, HabitRules.AT_MOST), "At most 2 days a week")

    @Test
    fun `a monthly check limit of 0 is not once`() =
        shows(habit(HabitRules.PER_MONTH, 0, HabitRules.AT_MOST), "Not once a month")

    @Test
    fun `a count limit for the week says every week, its number is in the limit text`() =
        shows(habit(HabitRules.PER_WEEK, 1, HabitRules.AT_MOST, HabitRules.COUNT), "Every week")
}
