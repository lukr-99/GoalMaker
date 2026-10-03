package com.goalmaker.app.ui.calendar

import android.app.Application
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onChildren
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.height
import androidx.compose.ui.unit.width
import com.goalmaker.app.application.planning.CalendarDay
import com.goalmaker.app.application.planning.TaskItem
import com.goalmaker.app.application.planning.TaskState
import com.goalmaker.app.ui.TestTheme
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** A calendar day cell as a screen reader hears it, and at the largest text size (M6-05). */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, qualifiers = "w360dp-h640dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class DayCellAccessibilityTest {
    @get:Rule
    val rule = createComposeRule()

    private val saturday = LocalDate.of(2026, 10, 3)
    private fun task(id: String) = TaskItem(id = id, title = "Task $id", state = TaskState.OPEN, topPriority = false, createdAt = "2026-10-01T10:00:00Z")
    private val busy = CalendarDay(saturday, planned = listOf(task("a"), task("b")), deadlines = listOf(task("c")), reminders = 1)

    // A week row the way the grid draws it: seven cells sharing the width.
    private fun show(day: CalendarDay, today: Boolean = false, selected: Boolean = false, fontScale: Float = 1f, onClick: () -> Unit = {}) {
        rule.setContent {
            TestTheme(fontScale) {
                Row(Modifier.fillMaxWidth()) {
                    DayCell(day, today, inPeriod = true, selected = selected, onClick = onClick, onDropTask = {}, modifier = Modifier.weight(1f))
                    repeat(6) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
    }

    @Test
    fun `a cell says its whole date and what is on it`() {
        var opened = 0
        show(busy, today = true, onClick = { opened++ })

        rule.onNode(hasClickAction())
            .assertContentDescriptionEquals("Saturday 3 October, today, 2 planned, 1 due, 1 reminder")
            // The day's number is in the description, so it isn't read twice.
            .assert(SemanticsMatcher.keyNotDefined(SemanticsProperties.Text))
            .performClick()
        assertEquals(1, opened)
    }

    @Test
    fun `an empty day and the open day say so`() {
        show(CalendarDay(saturday), selected = true)

        rule.onNode(hasClickAction()).assertContentDescriptionEquals("Saturday 3 October, open, nothing on it")
    }

    // A narrow phone, where the grid's shape alone leaves no room under a doubled number for the bar.
    @Test
    @Config(qualifiers = "w320dp-h568dp")
    fun `at the largest text size the cell grows to hold its number and bar`() {
        show(busy, today = true, fontScale = 2f)
        val cell = rule.onNodeWithContentDescription("Saturday 3 October", substring = true)
        cell.assertHasClickAction()

        val box = cell.getUnclippedBoundsInRoot()
        assertTrue("keeps at least the grid's shape", box.height >= box.width / 0.9f - 1.dp)
        val number = rule.onNodeWithContentDescription("Saturday 3 October", substring = true, useUnmergedTree = true)
            .onChildren()[0]
            .getUnclippedBoundsInRoot()
        // The bar sits 2 dp under the number and is 4 dp high.
        assertTrue("$number and the bar fit in the cell $box", number.top >= box.top && number.bottom + 6.dp <= box.bottom + 0.5.dp)
    }
}
