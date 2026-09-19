package com.goalmaker.app.ui.habits

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.goalmaker.app.application.planning.GoalList
import com.goalmaker.app.application.planning.HabitDraft
import com.goalmaker.app.application.planning.HabitList
import com.goalmaker.app.domain.planning.PlanningDay
import java.time.LocalDate
import java.time.LocalDateTime
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * The Habits screen (docs/habits.md, spec stories 36 to 42): each habit with today's ring, its streak
 * and heatmap; checking in, skipping, pausing, archiving and editing. Disk work runs on [io]; [clock]
 * and [dayStartHour] give the planning day every check-in lands on.
 */
class HabitsViewModel(
    private val habits: HabitList,
    goals: GoalList,
    private val dayStartHour: StateFlow<Int>,
    private val io: CoroutineDispatcher,
    private val clock: () -> LocalDateTime,
) : ViewModel() {
    val uiState: StateFlow<HabitsUiState> = combine(
        habits.watch().flowOn(io),
        goals.watch().flowOn(io),
        dayStartHour,
    ) { data, (goalList, _), startHour ->
        HabitBoard.build(data, goalList, PlanningDay.of(clock(), startHour))
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HabitsUiState())

    /** The planning day check-ins land on. */
    fun today(): LocalDate = PlanningDay.of(clock(), dayStartHour.value)

    /** Adds a habit starting today when [id] is null, otherwise changes it. False when it isn't valid. */
    suspend fun save(id: String?, draft: HabitDraft): Boolean = withContext(io) {
        if (id == null) habits.add(draft.copy(startsOn = today())) != null else habits.update(id, draft)
    }

    /** A tap on the ring: a check toggles, a count adds one. False for an amount, which asks for the value. */
    suspend fun tap(id: String): Boolean = withContext(io) { habits.tap(id, today()) }

    /** Adds [amount] to today's value. */
    fun checkIn(id: String, amount: Double) = write { habits.checkIn(id, today(), amount) }

    /** Clears today's value, for a check-in made by mistake. */
    fun clearToday(id: String) = write { habits.setValue(id, today(), 0.0) }

    /** Skips today's period (sick, travelling) or takes the skip back. */
    fun skip(id: String, skipped: Boolean) = write { habits.skip(id, today(), skipped) }

    fun pause(id: String) = write { habits.pause(id, today()) }

    fun resume(id: String) = write { habits.resume(id, today()) }

    fun setArchived(id: String, archived: Boolean) = write { habits.setArchived(id, archived) }

    fun delete(id: String) = write { habits.delete(id) }

    private fun write(work: () -> Unit) {
        viewModelScope.launch(io) { work() }
    }
}
