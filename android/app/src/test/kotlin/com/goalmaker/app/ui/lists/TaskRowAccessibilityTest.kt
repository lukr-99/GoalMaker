package com.goalmaker.app.ui.lists

import android.app.Application
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onRoot
import com.goalmaker.app.application.planning.AreaItem
import com.goalmaker.app.application.planning.TaskItem
import com.goalmaker.app.application.planning.TaskState
import com.goalmaker.app.ui.TestTheme
import com.goalmaker.app.ui.assertLaidOutIn
import java.time.LocalDate
import java.time.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** A task row as a screen reader hears it, and at the largest text size (M6-05). */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, qualifiers = "w360dp-h640dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class TaskRowAccessibilityTest {
    @get:Rule
    val rule = createComposeRule()

    private val title = "Write the quarterly report for the school board meeting next week"
    private val task = TaskItem(
        id = "t1",
        title = title,
        state = TaskState.OPEN,
        topPriority = true,
        createdAt = "2026-10-01T10:00:00Z",
        plannedDate = LocalDate.of(2026, 10, 3),
        plannedTime = LocalTime.of(21, 30),
        areaId = "a1",
        recurrence = "FREQ=DAILY",
    )
    private val area = AreaItem(id = "a1", name = "School", colorId = "blue", emoji = null)

    private fun show(fontScale: Float = 1f) {
        rule.setContent {
            TestTheme(fontScale) {
                TaskRow(task, area, showDay = false, onComplete = {}, onDelete = {}, onRemind = {}, onOpen = {}, reminded = true, tick = {})
            }
        }
    }

    @Test
    fun `the row reads as one item with its details, time and actions`() {
        show()

        // One node holds the title, so a screen reader doesn't stop on the text and the row apart.
        rule.onAllNodesWithText(title).assertCountEquals(1)
        val row = rule.onNode(hasText(title) and hasClickAction())
            .assert(hasText("School"))
            .assert(hasText("9:30", substring = true))
            .assert(SemanticsMatcher("names the reminder") { "Reminder set" in it.config.getOrNull(SemanticsProperties.ContentDescription).orEmpty() })
        val actions = row.fetchSemanticsNode().config.getOrNull(SemanticsActions.CustomActions).orEmpty().map { it.label }
        assertEquals(listOf("Delete $title", "Remind me"), actions)
    }

    @Test
    fun `the checkbox is its own control, named by the title`() {
        show()

        rule.onNode(isToggleable())
            .assertContentDescriptionEquals("$title done")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.ToggleableState, ToggleableState.Off))
            .assertHasClickAction()
    }

    @Test
    fun `at the largest text size the box, the title and the time stay on screen`() {
        show(fontScale = 2f)
        val root = rule.onRoot()

        rule.onNode(isToggleable()).assertIsDisplayed().assertLaidOutIn(root)
        rule.onNode(hasText(title) and hasClickAction()).assertIsDisplayed().assertLaidOutIn(root)
        rule.onAllNodesWithText("9:30", substring = true, useUnmergedTree = true)[0].assertLaidOutIn(root)
        rule.onAllNodesWithText(title, useUnmergedTree = true)[0].assertLaidOutIn(root)
    }
}
