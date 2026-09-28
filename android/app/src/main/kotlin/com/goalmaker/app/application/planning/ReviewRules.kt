package com.goalmaker.app.application.planning

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

    /** The id of [owner]'s [kind] review for the period starting on [periodStart]. */
    fun idOf(owner: String, kind: String, periodStart: LocalDate): String =
        NameBasedUuid.of(NAMESPACE, "review/${owner.lowercase(Locale.ROOT)}/$kind/$periodStart")
}
