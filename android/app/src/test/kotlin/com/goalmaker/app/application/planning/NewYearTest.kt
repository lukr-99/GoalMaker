package com.goalmaker.app.application.planning

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Test

/** What the January nudge counts (docs/reviews.md): the vectors take the counts, this checks the counting. */
class NewYearTest {
    private val today = LocalDate.parse("2027-01-04")
    private val january = LocalDate.parse("2027-01-01")

    @Test
    fun `a dropped or deleted year goal, or last year's, doesn't count as this year's`() {
        val goals = listOf(
            GoalItem("a", "Run", GoalHorizon.YEAR, january, status = GoalRules.DROPPED),
            GoalItem("b", "Read", GoalHorizon.YEAR, january, deleted = true),
            GoalItem("c", "Swim", GoalHorizon.YEAR, LocalDate.parse("2026-01-01")),
            GoalItem("d", "Walk", GoalHorizon.MONTH, january),
        )

        assertEquals(NewYearNudge(2027, review = true, goals = true), ReviewRules.newYearFor(today, goals, emptyList(), null))
        assertEquals(
            NewYearNudge(2027, review = true, goals = false),
            ReviewRules.newYearFor(today, goals + GoalItem("e", "Learn", GoalHorizon.YEAR, january), emptyList(), null),
        )
    }

    @Test
    fun `only a written yearly review of last year counts`() {
        val lastYear = LocalDate.parse("2026-01-01")
        val empty = ReviewItem("r1", ReviewRules.YEARLY, lastYear)
        val written = ReviewItem("r2", ReviewRules.YEARLY, lastYear, mood = 4)
        val monthly = ReviewItem("r3", ReviewRules.MONTHLY, LocalDate.parse("2026-12-01"), mood = 4)

        assertEquals(true, ReviewRules.newYearFor(today, emptyList(), listOf(empty, monthly), null)?.review)
        assertEquals(false, ReviewRules.newYearFor(today, emptyList(), listOf(written), null)?.review)
    }
}
