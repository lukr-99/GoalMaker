package com.goalmaker.app.application.planning

import com.goalmaker.app.domain.notes.LightMarkdown
import com.goalmaker.app.domain.planning.NameBasedUuid
import com.goalmaker.app.domain.planning.ReviewReminder
import java.time.LocalDate
import java.util.Locale

/**
 * Which period a review covers and the id every writer gives it (supabase/migrations/0008_reviews.sql,
 * contracts/vectors/reviews.json), so a review written on the phone, the PC or through the connector
 * is one row.
 */
object ReviewRules {
    const val WEEKLY = "weekly"
    const val MONTHLY = "monthly"
    const val YEARLY = "yearly"
    private const val NAMESPACE = "b8c61b22-5e0c-4f0a-9d1e-6f4a2c7e3b91"

    /** The start of the [kind] period [day] falls in: its week's Monday, its month's first day, or January 1. */
    fun periodStart(kind: String, day: LocalDate): LocalDate = when (kind) {
        WEEKLY -> day.minusDays((day.dayOfWeek.value - 1).toLong())
        MONTHLY -> day.withDayOfMonth(1)
        YEARLY -> day.withDayOfYear(1)
        else -> throw IllegalArgumentException("Unknown review kind: $kind")
    }

    /**
     * Whether the [kind] review reminder ringing on [day] can say the letter is here: the review of the
     * period it looks back on has a summary a Claude routine wrote (docs/letter.md, 'letterWaiting' in
     * contracts/vectors/reminders.json). The reminder reads [reviews] as they are when it rings.
     */
    fun letterWaiting(kind: String, day: LocalDate, reviews: List<ReviewItem>): Boolean {
        val start = ReviewReminder.periodStart(kind, day)
        return reviews.any { !it.deleted && it.kind == kind && it.periodStart == start && it.summary.isNotBlank() }
    }

    /**
     * The line of a letter the Reviews list shows: its first line of text that isn't a heading, or its
     * first heading when it has nothing else, without the Markdown marks. Null when there is no letter.
     */
    fun letterPreview(summary: String): String? {
        val lines = LightMarkdown.parse(summary)
            .map { block -> block to block.spans.joinToString("") { it.text }.trim() }
            .filter { (_, text) -> text.isNotEmpty() }
        return (lines.firstOrNull { (block, _) -> block.heading == 0 } ?: lines.firstOrNull())?.second
    }

    /**
     * The January nudge for planning day [today] (spec story 66, 'newYear' in contracts/vectors/reviews.json):
     * null outside January, once the owner dismissed it this year ([dismissedYear] is the year it was last
     * dismissed), or when last year is reviewed and this year has goals. [yearGoals] counts this year's year
     * goals that are neither deleted nor dropped.
     */
    fun newYear(today: LocalDate, yearGoals: Int, lastYearReviewed: Boolean, dismissedYear: Int?): NewYearNudge? {
        if (today.monthValue != 1 || dismissedYear == today.year) return null
        val nudge = NewYearNudge(today.year, review = !lastYearReviewed, goals = yearGoals == 0)
        return nudge.takeIf { it.review || it.goals }
    }

    /**
     * [newYear] from what the owner has: this year's year goals that are neither deleted nor dropped, and
     * whether last year's yearly review was written.
     */
    fun newYearFor(today: LocalDate, goals: List<GoalItem>, reviews: List<ReviewItem>, dismissedYear: Int?): NewYearNudge? {
        val thisYear = LocalDate.of(today.year, 1, 1)
        val yearGoals = goals.count { !it.deleted && it.horizon == GoalHorizon.YEAR && it.periodStart == thisYear && it.status != GoalRules.DROPPED }
        val reviewed = reviews.any { !it.deleted && it.kind == YEARLY && it.periodStart == thisYear.minusYears(1) && it.written }
        return newYear(today, yearGoals, reviewed, dismissedYear)
    }

    /** The id of [owner]'s [kind] review for the period starting on [periodStart]. */
    fun idOf(owner: String, kind: String, periodStart: LocalDate): String =
        NameBasedUuid.of(NAMESPACE, "review/${owner.lowercase(Locale.ROOT)}/$kind/$periodStart")
}
