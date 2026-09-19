package com.goalmaker.app.ui.review

import com.goalmaker.app.application.planning.ReviewItem
import java.time.LocalDate

/**
 * The Reviews screen: the periods a review can be written for (this week and month, and the ones just
 * gone), and the reviews already written.
 */
data class ReviewsUiState(
    val loaded: Boolean = false,
    val weekStart: LocalDate = LocalDate.MIN,
    val monthStart: LocalDate = LocalDate.MIN,
    val lastWeekStart: LocalDate = LocalDate.MIN,
    val lastMonthStart: LocalDate = LocalDate.MIN,
    val past: List<ReviewItem> = emptyList(),
)
