package com.goalmaker.app.ui.places

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.goalmaker.app.application.planning.GoalList
import com.goalmaker.app.application.planning.HabitList
import com.goalmaker.app.application.planning.ReviewList
import com.goalmaker.app.application.planning.TaskList
import com.goalmaker.app.application.settings.SettingsStore
import com.goalmaker.app.domain.navigation.DeviceKind
import com.goalmaker.app.domain.navigation.PlaceRules
import com.goalmaker.app.domain.planning.PlanningDay
import java.time.LocalDateTime
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
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

    private val digest = combine(
        combine(tasks.watchAll().flowOn(io), habits.watch().flowOn(io), ::Pair),
        goals.watch().flowOn(io),
        reviews.watch().flowOn(io),
        settings.dayStartHour,
        minutes,
    ) { (taskList, habitData), (goalList, entries), reviewList, startHour, _ ->
        PlacesBoard.build(taskList, habitData, goalList, entries, reviewList, PlanningDay.of(clock(), startHour))
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
