package com.goalmaker.app.ui.composer

import android.app.Application
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertHasNoClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import com.goalmaker.app.domain.composer.SpanKind
import com.goalmaker.app.ui.TestTheme
import com.goalmaker.app.ui.assertLaidOutIn
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The composer's preview chips as a screen reader and a keyboard meet them, and at the largest text size (M6-05). */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, qualifiers = "w360dp-h640dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ComposerBarAccessibilityTest {
    @get:Rule
    val rule = createComposeRule()

    private val tomorrow = ComposerChip(SpanKind.DATE, "Tomorrow", null, false, null, emptyList())
    private val shop = ComposerChip(SpanKind.TAG, "#shop", "new", false, null, emptyList())

    private fun show(chips: List<ComposerChip>, onRemove: ((ComposerChip) -> Unit)?, fontScale: Float = 1f) {
        rule.setContent {
            TestTheme(fontScale) {
                ComposerBar(
                    state = rememberTextFieldState("Buy milk tomorrow #shop"),
                    chips = chips,
                    canSend = true,
                    onSubmit = {},
                    onRemove = onRemove,
                )
            }
        }
    }

    @Test
    fun `a removable chip is one button that says its label and that it removes`() {
        val removed = mutableListOf<ComposerChip>()
        show(listOf(tomorrow, shop), onRemove = { removed += it })

        rule.onNode(hasText("Tomorrow") and hasClickAction())
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button))
            .assert(SemanticsMatcher.keyNotDefined(SemanticsProperties.Selected))
            .assertContentDescriptionEquals("Remove")
            .performClick()
        rule.onNode(hasText("#shop · new") and hasClickAction()).assertContentDescriptionEquals("Remove")

        assertEquals(listOf(tomorrow), removed)
    }

    @Test
    fun `a read-only chip is one item, and a warning one says so`() {
        val price = ComposerChip(SpanKind.IDEA, "Waits 7 days", null, false, null, emptyList())
        val why = ComposerChip(SpanKind.IDEA, "Add the why: because …", null, false, null, emptyList(), warning = true)
        show(listOf(price, why), onRemove = null)

        rule.onNodeWithText("Waits 7 days").assertHasNoClickAction()
            .assert(SemanticsMatcher.keyNotDefined(SemanticsProperties.ContentDescription))
        rule.onNodeWithText("Add the why: because …").assertHasNoClickAction()
            .assertContentDescriptionEquals("Warning")
    }

    // Robolectric's native graphics keep the view in touch mode, where Tab skips past the chips.
    @Test
    @GraphicsMode(GraphicsMode.Mode.LEGACY)
    fun `the chips come before the line, on screen and for the keyboard`() {
        show(listOf(tomorrow, shop), onRemove = {})

        val chip = rule.onNode(hasText("Tomorrow") and hasClickAction()).fetchSemanticsNode().boundsInRoot
        val line = rule.onNode(hasSetTextAction()).fetchSemanticsNode().boundsInRoot
        assertTrue("the chips sit above the line", chip.bottom <= line.top)

        // Tab on a hardware keyboard, which also leaves touch mode so the chips can take the focus.
        val order = listOf(
            hasText("Tomorrow") and hasClickAction(),
            hasText("#shop · new") and hasClickAction(),
            hasSetTextAction(),
        )
        order.forEach { node ->
            rule.onRoot().performKeyInput { pressKey(Key.Tab) }
            rule.onNode(node).assertIsFocused()
        }
        rule.onRoot().performKeyInput { pressKey(Key.Tab) }
        rule.onNodeWithContentDescription("Add task").assertIsFocused()
    }

    @Test
    fun `at the largest text size every chip, the line and the button stay on screen`() {
        val repeat = ComposerChip(SpanKind.REPEAT, "Every Monday, Wednesday and Friday", null, false, null, emptyList())
        show(listOf(tomorrow, shop, repeat), onRemove = {}, fontScale = 2f)
        val root = rule.onRoot()

        listOf("Tomorrow", "#shop · new", "Every Monday, Wednesday and Friday").forEach { label ->
            rule.onNode(hasText(label) and hasClickAction()).assertIsDisplayed().assertLaidOutIn(root)
        }
        rule.onNode(hasSetTextAction()).assertIsDisplayed().assertLaidOutIn(root)
        rule.onNodeWithContentDescription("Add task").assertIsDisplayed().assertLaidOutIn(root)
    }
}
