package com.goalmaker.app.ui.review

import com.goalmaker.app.application.planning.GoalItem
import com.goalmaker.app.application.planning.ReviewDigest
import com.goalmaker.app.application.planning.ReviewItem
import com.goalmaker.app.domain.planning.ReviewQuestion
import com.goalmaker.app.ui.goals.GoalRow
import com.goalmaker.app.ui.tally.TallySlice

/**
 * The guided review (docs/reviews.md): where it is, what the period looked like, the prompts it asks
 * with the answers so far, how the period felt, and the goals of the period ahead.
 */
data class ReviewUiState(
    val loaded: Boolean = false,
    val step: ReviewStep = ReviewStep.LOOK_BACK,
    val kind: String = "weekly",
    val digest: ReviewDigest = ReviewDigest(),
    val review: ReviewItem? = null,
    val questions: List<ReviewQuestion> = emptyList(),
    val answers: Map<String, String> = emptyMap(),
    val nextGoals: List<GoalRow> = emptyList(),
    val canCopyGoals: Boolean = false,
    val goals: List<GoalItem> = emptyList(),
    /** Where Tally says the period's time went, most first; empty when nothing was counted (docs/tally.md). */
    val tally: List<TallySlice> = emptyList(),
) {
    /** The letter a Claude routine wrote about the period, blank when there is none (docs/letter.md). */
    val letter: String get() = review?.summary.orEmpty()

    val hasLetter: Boolean get() = letter.isNotBlank()

    /** The steps this review walks through: the Letter only when there is one. */
    val steps: List<ReviewStep> get() = if (hasLetter) ReviewStep.entries else ReviewStep.entries - ReviewStep.LETTER

    val mood: Int? get() = review?.mood

    val energy: Int? get() = review?.energy

    /** How many tasks of the period are still open, for the step that handles them. */
    val openTasks: Int get() = digest.openTasks.size

    /** How many prompts were actually answered. */
    val answered: Int get() = answers.values.count { it.isNotBlank() }
}
