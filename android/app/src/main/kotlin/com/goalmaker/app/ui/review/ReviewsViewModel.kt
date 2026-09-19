package com.goalmaker.app.ui.review

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.goalmaker.app.application.planning.ReviewItem
import com.goalmaker.app.application.planning.ReviewList
import com.goalmaker.app.application.planning.ReviewLookBack
import com.goalmaker.app.application.planning.ReviewRules
import com.goalmaker.app.application.settings.SettingsStore
import com.goalmaker.app.domain.planning.PlanningDay
import java.time.LocalDate
import java.time.LocalDateTime
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * The reviews already written and the ones waiting (docs/reviews.md): last week's and last month's
 * review can be started or picked up here, and any past one read back.
 */
class ReviewsViewModel(
    private val reviews: ReviewList,
    private val settings: SettingsStore,
    private val io: CoroutineDispatcher,
    private val clock: () -> LocalDateTime,
) : ViewModel() {
    val uiState: StateFlow<ReviewsUiState> = reviews.watch().flowOn(io).map { all ->
        val today = today()
        ReviewsUiState(
            loaded = true,
            weekStart = ReviewRules.periodStart(ReviewRules.WEEKLY, today),
            monthStart = ReviewRules.periodStart(ReviewRules.MONTHLY, today),
            lastWeekStart = ReviewLookBack.previousStart(ReviewRules.WEEKLY, ReviewRules.periodStart(ReviewRules.WEEKLY, today)),
            lastMonthStart = ReviewLookBack.previousStart(ReviewRules.MONTHLY, ReviewRules.periodStart(ReviewRules.MONTHLY, today)),
            past = all.filter(ReviewItem::written),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ReviewsUiState())

    /** Removes a review the owner doesn't want to keep. */
    fun delete(id: String) {
        viewModelScope.launch(io) { reviews.delete(id) }
    }

    private fun today(): LocalDate = PlanningDay.of(clock(), settings.dayStartHour.value)
}
