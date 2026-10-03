package com.goalmaker.app.ui.settings

import android.app.Application
import androidx.compose.foundation.layout.Column
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import com.goalmaker.app.ui.TestTheme
import com.goalmaker.app.ui.assertLaidOutIn
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Settings rows at the largest text size, and a switch row as one control (M6-05). */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, qualifiers = "w360dp-h640dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SettingsRowsAccessibilityTest {
    @get:Rule
    val rule = createComposeRule()

    private fun show(fontScale: Float) {
        rule.setContent {
            TestTheme(fontScale) {
                Column {
                    ToggleRow("Completion sound", "A soft tick when you finish something.", checked = true, onCheckedChange = {})
                    ButtonRow("Updates", "Looks for a newer version.", button = "Check for updates now", onClick = {})
                }
            }
        }
    }

    @Test
    fun `a switch row is one switch named by its title`() {
        show(fontScale = 1f)

        rule.onNode(isToggleable())
            .assert(hasText("Completion sound"))
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Switch))
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.ToggleableState, ToggleableState.On))
    }

    @Test
    fun `at the largest text size a long button wraps and leaves its title room`() {
        show(fontScale = 2f)
        val root = rule.onRoot()
        val width = root.fetchSemanticsNode().boundsInRoot.width

        val button = rule.onNode(hasText("Check for updates now") and hasClickAction()).assertIsDisplayed().assertLaidOutIn(root)
        rule.onNodeWithText("Updates", useUnmergedTree = true).assertIsDisplayed().assertLaidOutIn(root)
        // The hint wraps to the title's column, so its width is the room the title has.
        val hint = rule.onNodeWithText("Looks for a newer version.", useUnmergedTree = true).assertLaidOutIn(root)
        assertTrue("the title keeps a third of the row", hint.fetchSemanticsNode().boundsInRoot.width >= width / 3)
        assertTrue("the button takes at most 60 percent of the row", button.fetchSemanticsNode().boundsInRoot.width <= width * 0.6f)
        rule.onNode(isToggleable()).assertIsDisplayed().assertLaidOutIn(root)
    }
}
