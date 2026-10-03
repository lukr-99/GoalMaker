package com.goalmaker.app.ui.habits

import android.app.Application
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import com.goalmaker.app.application.planning.HabitDot
import com.goalmaker.app.application.planning.HabitItem
import com.goalmaker.app.application.planning.HabitPeriodState
import com.goalmaker.app.application.planning.HabitRules
import com.goalmaker.app.application.planning.HabitStanding
import com.goalmaker.app.ui.TestTheme
import com.goalmaker.app.ui.assertLaidOutIn
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** A habit card and its check-in button as a screen reader hears them, and at the largest text size (M6-05). */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, qualifiers = "w360dp-h640dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class HabitCardAccessibilityTest {
    @get:Rule
    val rule = createComposeRule()

    private val name = "Read before bed every evening"
    private val row = HabitRow(
        habit = HabitItem(id = "h1", name = name, startsOn = LocalDate.of(2026, 1, 1), measure = HabitRules.COUNT, target = 3.0),
        ring = 1.0 / 3,
        streak = 4,
        state = HabitPeriodState.NONE,
        value = 1.0,
        standing = HabitStanding.LEFT,
        dots = List(7) { HabitDot.NONE },
    )

    private fun show(fontScale: Float = 1f, onCheckIn: () -> Unit = {}) {
        rule.setContent {
            TestTheme(fontScale) {
                HabitCard(row, LocalDate.of(2026, 10, 3), full = false, onCheckIn = onCheckIn, onMenu = {}, onSkip = {})
            }
        }
    }

    @Test
    fun `the check-in button says what a tap does and where today stands`() {
        var taps = 0
        show(onCheckIn = { taps++ })

        rule.onNodeWithContentDescription("Add one to $name")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button))
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "1 of 3 today"))
            // The +1 on it is drawn, not read again after the name.
            .assert(SemanticsMatcher.keyNotDefined(SemanticsProperties.Text))
            .assertHasClickAction()
            .performClick()

        assertEquals(1, taps)
    }

    @Test
    fun `the card reads its name, streak and day as one item`() {
        show()

        rule.onAllNodesWithText(name).assertCountEquals(1)
        rule.onNode(hasText(name) and hasClickAction())
            .assert(hasText("1 of 3 today"))
            .assert(SemanticsMatcher("says the streak") { "4-day streak" in it.config.getOrNull(SemanticsProperties.ContentDescription).orEmpty() })
    }

    @Test
    fun `at the largest text size the name, the button and the menu stay on screen`() {
        show(fontScale = 2f)
        val root = rule.onRoot()

        rule.onAllNodesWithText(name, useUnmergedTree = true)[0].assertIsDisplayed().assertLaidOutIn(root)
        rule.onNodeWithContentDescription("Add one to $name").assertIsDisplayed().assertLaidOutIn(root)
        rule.onNodeWithContentDescription("More for $name").assertIsDisplayed().assertLaidOutIn(root)
    }
}
