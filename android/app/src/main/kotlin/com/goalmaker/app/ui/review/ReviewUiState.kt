package com.goalmaker.app.ui.review

import com.goalmaker.app.application.planning.GoalItem
import com.goalmaker.app.application.planning.ReviewDigest
import com.goalmaker.app.application.planning.ReviewItem
import com.goalmaker.app.domain.planning.ReviewQuestion
import com.goalmaker.app.ui.goals.GoalRow

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
) {
    val mood: Int? get() = review?.mood

    val energy: Int? get() = review?.energy

    /** How many tasks of the period are still open, for the step that handles them. */
    val openTasks: Int get() = digest.openTasks.size

    /** How many prompts were actually answered. */
    val answered: Int get() = answers.values.count { it.isNotBlank() }
}
