package com.goalmaker.app.ui.components

import android.app.Application
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import com.goalmaker.app.ui.TestTheme
import com.goalmaker.app.ui.assertLaidOutIn
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The chosen emoji is a picture in its box, whole at the largest text size (M6-05). */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, qualifiers = "w360dp-h640dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class EmojiFieldAccessibilityTest {
    @get:Rule
    val rule = createComposeRule()

    @Test
    fun `at the largest text size the emoji stays inside its box`() {
        rule.setContent { TestTheme(fontScale = 2f) { EmojiField("🧘", onChange = {}) } }

        val box = rule.onNodeWithContentDescription("Pick an emoji").assertHasClickAction()
        rule.onNodeWithText("🧘", useUnmergedTree = true).assertLaidOutIn(box)
    }
}
