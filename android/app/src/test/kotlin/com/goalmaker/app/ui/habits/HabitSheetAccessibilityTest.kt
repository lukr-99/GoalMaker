package com.goalmaker.app.ui.habits

import android.app.Application
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import com.goalmaker.app.application.planning.HabitItem
import com.goalmaker.app.application.planning.HabitPeriodState
import com.goalmaker.app.application.planning.HabitStanding
import com.goalmaker.app.application.planning.TaskItem
import com.goalmaker.app.application.planning.TaskState
import com.goalmaker.app.ui.TestTheme
import com.goalmaker.app.ui.lists.ReminderSheet
import java.time.LocalDate
import java.time.LocalTime
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The habit menu and the reminder sheet scroll, so at the largest text size the last choice stays in reach (M6-05). */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, qualifiers = "w360dp-h640dp")
class HabitSheetAccessibilityTest {
    @get:Rule
    val rule = createComposeRule()

    @Test
    fun `the habit menu scrolls to its last choice`() {
        val row = HabitRow(
            habit = HabitItem(id = "h1", name = "Stretch", startsOn = LocalDate.of(2026, 1, 1)),
            ring = 0.0,
            streak = 0,
            state = HabitPeriodState.NONE,
            standing = HabitStanding.LEFT,
        )
        rule.setContent {
            TestTheme(fontScale = 2f) {
                HabitSheet(
                    row = row,
                    onDismiss = {},
                    onCheckIn = {},
                    onLog = {},
                    onSkip = {},
                    onFail = {},
                    onClear = {},
                    onPause = {},
                    onEdit = {},
                    onArchive = {},
                    onDelete = {},
                )
            }
        }

        rule.onNode(hasScrollAction() and hasAnyDescendant(hasText("Delete"))).assertExists()
    }

    @Test
    fun `the reminder sheet scrolls to its last choice`() {
        val task = TaskItem(
            id = "t1",
            title = "Call the dentist",
            state = TaskState.OPEN,
            topPriority = false,
            createdAt = "2026-10-01T10:00:00Z",
            plannedDate = LocalDate.of(2026, 10, 3),
            plannedTime = LocalTime.of(9, 0),
        )
        rule.setContent {
            TestTheme(fontScale = 2f) {
                ReminderSheet(task, emptyList(), {}, {}, {}, {}, {}, {})
            }
        }

        rule.onNode(hasScrollAction() and hasAnyDescendant(hasText("Tomorrow morning"))).assertExists()
    }
}
