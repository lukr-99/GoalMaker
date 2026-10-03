package com.goalmaker.app.ui.nav

import android.app.Application
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isSelectable
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onRoot
import com.goalmaker.app.domain.navigation.PlaceRules
import com.goalmaker.app.ui.TestTheme
import com.goalmaker.app.ui.assertLaidOutIn
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The bottom navigation as a screen reader hears it, and at the largest text size (M6-05). */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, qualifiers = "w360dp-h640dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class MainNavigationBarAccessibilityTest {
    @get:Rule
    val rule = createComposeRule()

    private val pins = listOf(PlaceRules.TODAY, PlaceRules.HABITS, PlaceRules.GOALS, PlaceRules.CALENDAR)
    private val labels = listOf("Today", "Habits", "Goals", "Calendar", "Places")

    private fun show(fontScale: Float) {
        rule.setContent {
            TestTheme(fontScale) { MainNavigationBar(pins, PlaceRules.TODAY, placesCount = 3, onSelect = {}) }
        }
    }

    @Test
    fun `every place is a tab named by its label, and Places says what waits`() {
        show(fontScale = 1f)

        labels.forEach { label -> rule.onNode(isSelectable() and hasText(label)).assertIsDisplayed() }
        rule.onNode(isSelectable() and hasText("Today")).assertIsSelected()
        rule.onNode(isSelectable() and hasText("Places"))
            .assert(SemanticsMatcher("says what waits") { it.config.getOrNull(SemanticsProperties.ContentDescription) == listOf("Places, 3 waiting") })
    }

    @Test
    fun `at the largest text size every label stays inside the bar`() {
        show(fontScale = 2f)
        val root = rule.onRoot()

        labels.forEach { label -> rule.onAllNodesWithText(label, useUnmergedTree = true)[0].assertLaidOutIn(root) }
    }
}
