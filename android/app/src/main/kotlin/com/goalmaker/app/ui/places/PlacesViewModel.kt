package com.goalmaker.app.ui.places

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.goalmaker.app.application.planning.GoalList
import com.goalmaker.app.application.planning.HabitList
import com.goalmaker.app.application.planning.ReviewList
import com.goalmaker.app.application.planning.TallyCategory
import com.goalmaker.app.application.planning.TallyFilter
import com.goalmaker.app.application.planning.TallyList
import com.goalmaker.app.application.planning.TaskList
import com.goalmaker.app.application.planning.LifeGoalList
import com.goalmaker.app.application.planning.LifeGoalRules
import com.goalmaker.app.application.planning.WantList
import com.goalmaker.app.application.settings.SettingsStore
import com.goalmaker.app.domain.navigation.DeviceKind
import com.goalmaker.app.domain.navigation.PlaceRules
import com.goalmaker.app.domain.planning.PlanningDay
import com.goalmaker.app.ui.tally.TallyBoard
import java.time.LocalDateTime
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/**
 * The Places hub (ADR 0014): a live tile for every place, and the pins the bottom bar shows, which
 * the owner edits here. Pins are this phone's own setting; the rules are [PlaceRules].
 */
class PlacesViewModel(
    tasks: TaskList,
    habits: HabitList,
    goals: GoalList,
    reviews: ReviewList,
    wants: WantList,
    lifeGoals: LifeGoalList,
    tally: TallyList,
    private val tallyCategories: List<TallyCategory>,
    private val settings: SettingsStore,
    io: CoroutineDispatcher,
    private val clock: () -> LocalDateTime,
) : ViewModel() {

    private val editing = MutableStateFlow(false)

    /** Ticks every minute so the numbers move on when the planning day does. */
    private val minutes = flow {
        while (true) {
            emit(Unit)
            delay(MINUTE)
        }
    }

    /** Tally's rows around today and the owner's categories, read again whenever either changes. */
    private val tallyDays = tally.watch().map {
        val around = clock().toLocalDate()
        tally.totals(around.minusDays(1), around.plusDays(1)) to tally.categories()
    }.flowOn(io)

    private val digest = combine(
        combine(tasks.watchAll().flowOn(io), habits.watch().flowOn(io), ::Pair),
        combine(goals.watch().flowOn(io), lifeGoals.watch().flowOn(io), ::Pair),
        combine(reviews.watch().flowOn(io), wants.watch().flowOn(io), tallyDays, ::Triple),
        settings.dayStartHour,
        minutes,
    ) { (taskList, habitData), (goalData, lifeGoalList), (reviewList, wantList, tallyData), startHour, _ ->
        val (goalList, entries) = goalData
        val today = PlanningDay.of(clock(), startHour)
        val (rows, own) = tallyData
        val tallyToday = TallyBoard.day(rows, today, TallyFilter(), TallyBoard.lookup(tallyCategories, own))
        PlacesBoard.build(taskList, habitData, goalList, entries, reviewList, today, wantList).copy(
            tallyToday = tallyToday.slices,
            lifeGoalsOpen = lifeGoalList.count { it.status == LifeGoalRules.OPEN },
        )
    }

    val uiState: StateFlow<PlacesUiState> = combine(digest, settings.pins, editing) { built, pins, isEditing ->
        PlacesUiState(digest = built, pins = pins, editing = isEditing)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = PlacesUiState(pins = settings.pins.value),
    )

    fun toggleEditing() {
        editing.value = !editing.value
    }

    fun stopEditing() {
        editing.value = false
    }

    /** Pins or unpins [place]; returns false when the rules refused (a fifth pin, or the last one). */
    fun togglePin(place: String): Boolean {
        val pins = settings.pins.value
        val result = if (place in pins) PlaceRules.unpin(pins, place) else PlaceRules.pin(pins, place, DeviceKind.PHONE)
        if (!result.refused) settings.setPins(result.pins)
        return !result.refused
    }

    private companion object {
        const val MINUTE = 60_000L
    }
}
