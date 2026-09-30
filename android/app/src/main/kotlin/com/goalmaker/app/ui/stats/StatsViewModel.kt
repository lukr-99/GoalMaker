package com.goalmaker.app.ui.stats

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.goalmaker.app.application.planning.GoalList
import com.goalmaker.app.application.planning.HabitList
import com.goalmaker.app.application.planning.ReviewList
import com.goalmaker.app.application.planning.StatsRules
import com.goalmaker.app.application.planning.TallyCategory
import com.goalmaker.app.application.planning.TallyFilter
import com.goalmaker.app.application.planning.TallyList
import com.goalmaker.app.application.planning.TallyRules
import com.goalmaker.app.application.planning.TaskList
import com.goalmaker.app.application.planning.WantList
import com.goalmaker.app.application.planning.WantRules
import com.goalmaker.app.application.settings.SettingsStore
import com.goalmaker.app.domain.planning.PlanningDay
import com.goalmaker.app.ui.tally.TallyBoard
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.temporal.TemporalAdjusters
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/**
 * The stats screen (docs/stats.md, spec stories 64 and 67): tasks finished week by week, goals hit
 * month by month, how the habits are holding up, the mood and energy of past reviews, what became
 * of the wants, and where Tally says the time went over twelve weeks.
 */
class StatsViewModel(
    tasks: TaskList,
    goals: GoalList,
    habits: HabitList,
    reviews: ReviewList,
    wants: WantList,
    tally: TallyList,
    tallyCategories: List<TallyCategory>,
    private val settings: SettingsStore,
    io: CoroutineDispatcher,
    private val clock: () -> LocalDateTime,
) : ViewModel() {
    /** The Tally block: twelve weeks of every device's minutes by category (docs/tally.md). */
    private val tallyWeeks = tally.watch().map {
        val today = today()
        val first = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).minusWeeks(TallyBoard.STATS_WEEKS - 1L)
        val rows = tally.totals(first, today)
        val categories = TallyBoard.lookup(tallyCategories, tally.categories())
        val weeks = TallyRules.weeks(rows, today, TallyBoard.STATS_WEEKS, TallyFilter())
        TallyBoard.weeks(weeks, categories) to TallyBoard.slices(TallyRules.byCategory(rows), categories)
    }.flowOn(io)

    val uiState: StateFlow<StatsUiState> = combine(
        tasks.watchAll().flowOn(io),
        goals.watch().flowOn(io),
        habits.watch().flowOn(io),
        reviews.watch().flowOn(io),
        combine(wants.watch().flowOn(io), wants.watchCooldowns().flowOn(io), tallyWeeks, ::Triple),
    ) { taskList, (goalList, entries), habitData, reviewList, (wantList, cooldowns, tallyBlock) ->
        val decided = WantRules.stats(wantList, cooldowns.currency)
        val (weeks, slices) = tallyBlock
        StatsUiState(
            loaded = true,
            digest = StatsRules.build(taskList, goalList, entries, habitData, reviewList, today()),
            wants = decided.takeIf { it.bought + it.dropped > 0 },
            currency = cooldowns.currency,
            tallyWeeks = if (weeks.any { it.minutes > 0 }) weeks else emptyList(),
            tallySlices = slices,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), StatsUiState())

    private fun today(): LocalDate = PlanningDay.of(clock(), settings.dayStartHour.value)
}
