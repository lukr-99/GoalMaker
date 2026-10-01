package com.goalmaker.app.ui.places

import com.goalmaker.app.application.planning.HabitCheckin
import com.goalmaker.app.application.planning.HabitData
import com.goalmaker.app.application.planning.HabitItem
import com.goalmaker.app.application.planning.ProjectRules
import com.goalmaker.app.application.planning.Reflection
import com.goalmaker.app.application.planning.ReviewItem
import com.goalmaker.app.application.planning.ReviewRules
import com.goalmaker.app.application.planning.TaskItem
import com.goalmaker.app.application.planning.TaskState
import com.goalmaker.app.domain.navigation.PlaceRules
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlacesBoardTest {
    // A Wednesday; last week started on Monday 2026-09-21.
    private val today = LocalDate.of(2026, 9, 30)

    private fun task(
        id: String,
        state: TaskState = TaskState.OPEN,
        planned: LocalDate? = null,
        project: String? = null,
        column: String? = null,
        completedAt: String? = null,
    ) = TaskItem(
        id = id,
        title = id,
        state = state,
        topPriority = false,
        createdAt = "2026-09-01T08:00:00Z",
        plannedDate = planned,
        projectId = project,
        boardColumn = column,
        completedAt = completedAt,
    )

    private fun build(tasks: List<TaskItem> = emptyList(), reviews: List<ReviewItem> = emptyList()) =
        PlacesBoard.build(tasks, HabitData(), emptyList(), emptyList(), reviews, today)

    @Test
    fun `the lists, the coming week, projects and the archive count what they show`() {
        val digest = build(
            listOf(
                task("today-open", planned = today),
                task("today-done", TaskState.DONE, planned = today, completedAt = "2026-09-30T09:00:00Z"),
                task("tomorrow", planned = today.plusDays(1)),
                task("in-a-week", planned = today.plusDays(7)),
                task("too-far", planned = today.plusDays(8)),
                task("inbox-1"),
                task("inbox-2"),
                task("todo-item", planned = today.plusDays(30), project = "p", column = ProjectRules.TODO),
                task("doing-item", planned = today.plusDays(30), project = "p", column = ProjectRules.DOING),
                task("old-done", TaskState.DONE, planned = today.minusDays(20), completedAt = "2026-09-10T09:00:00Z"),
            ),
        )

        assertEquals(1, digest.todayDone)
        assertEquals(2, digest.todayTotal)
        assertEquals(1, digest.tomorrow)
        assertEquals(2, digest.inbox)
        assertEquals(2, digest.comingWeek)
        assertEquals(2, digest.projectsOpen)
        assertEquals(1, digest.projectsDoing)
        assertEquals(1, digest.doneThisWeek)
        assertEquals(2, digest.archived)
    }

    @Test
    fun `the habits tile counts a habit kept off Today`() {
        val habits = HabitData(
            habits = listOf(
                HabitItem("read", "Read", startsOn = today),
                HabitItem("floss", "Floss", startsOn = today, showOnToday = false),
            ),
            checkins = listOf(HabitCheckin("c", "floss", today, value = 1.0)),
        )

        val digest = PlacesBoard.build(emptyList(), habits, emptyList(), emptyList(), emptyList(), today)

        assertEquals(2, digest.habitsDue)
        assertEquals(1, digest.habitsMet)
    }

    @Test
    fun `last week's letter waits until the review is started`() {
        val letter = ReviewItem("r", ReviewRules.WEEKLY, LocalDate.of(2026, 9, 21), summary = "Dear me")
        assertTrue(build(reviews = listOf(letter)).letterWaiting)
        assertFalse(build(reviews = listOf(letter.copy(mood = 4))).letterWaiting)
        assertFalse(build(reviews = listOf(letter.copy(reflections = listOf(Reflection("wins/proud", "shipped"))))).letterWaiting)
        assertFalse(build(reviews = listOf(letter.copy(periodStart = LocalDate.of(2026, 9, 14)))).letterWaiting)
        assertFalse(build(reviews = listOf(letter.copy(summary = ""))).letterWaiting)
    }

    @Test
    fun `what waits is the inbox and a letter`() {
        val digest = build(
            tasks = listOf(task("inbox-1"), task("inbox-2")),
            reviews = listOf(ReviewItem("r", ReviewRules.WEEKLY, LocalDate.of(2026, 9, 21), summary = "Dear me")),
        )
        assertEquals(mapOf(PlaceRules.INBOX to 2, PlaceRules.REVIEWS to 1), digest.waiting)
        assertEquals(emptyMap<String, Int>(), build().waiting)
    }
}
