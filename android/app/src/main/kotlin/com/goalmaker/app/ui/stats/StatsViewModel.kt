package com.goalmaker.app.ui.stats

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.goalmaker.app.application.planning.GoalList
import com.goalmaker.app.application.planning.HabitList
import com.goalmaker.app.application.planning.ReviewList
import com.goalmaker.app.application.planning.StatsRules
import com.goalmaker.app.application.planning.TaskList
import com.goalmaker.app.application.settings.SettingsStore
import com.goalmaker.app.domain.planning.PlanningDay
import java.time.LocalDate
import java.time.LocalDateTime
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn

/**
 * The stats screen (docs/stats.md, spec stories 64 and 67): tasks finished week by week, goals hit
 * month by month, how the habits are holding up, and the mood and energy of past reviews.
 */
class StatsViewModel(
    tasks: TaskList,
    goals: GoalList,
    habits: HabitList,
    reviews: ReviewList,
    private val settings: SettingsStore,
    io: CoroutineDispatcher,
    private val clock: () -> LocalDateTime,
) : ViewModel() {
    val uiState: StateFlow<StatsUiState> = combine(
        tasks.watchAll().flowOn(io),
        goals.watch().flowOn(io),
        habits.watch().flowOn(io),
        reviews.watch().flowOn(io),
    ) { taskList, (goalList, entries), habitData, reviewList ->
        StatsUiState(
            loaded = true,
            digest = StatsRules.build(taskList, goalList, entries, habitData, reviewList, today()),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), StatsUiState())

    private fun today(): LocalDate = PlanningDay.of(clock(), settings.dayStartHour.value)
}
