package com.goalmaker.app.ui.habits

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.goalmaker.app.application.composer.QuickAddLines
import com.goalmaker.app.application.planning.GoalList
import com.goalmaker.app.application.planning.HabitDraft
import com.goalmaker.app.application.planning.HabitList
import com.goalmaker.app.domain.planning.PlanningDay
import com.goalmaker.app.ui.composer.LineOutcome
import com.goalmaker.app.ui.lists.UndoEvent
import java.time.LocalDate
import java.time.LocalDateTime
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * The Habits screen (docs/habits.md, spec stories 36 to 42): the summary card, each habit in its group
 * with today's check-in, its streak, week and heatmap, Hide done; checking in, skipping, pausing,
 * archiving and editing. Disk work runs on [io]; [clock]
 * and [dayStartHour] give the planning day every check-in lands on.
 */
class HabitsViewModel(
    private val habits: HabitList,
    goals: GoalList,
    private val dayStartHour: StateFlow<Int>,
    private val io: CoroutineDispatcher,
    private val clock: () -> LocalDateTime,
) : ViewModel() {
    // Hide done is the screen's own, kept while the app runs (docs/habits.md).
    private val hideDone = MutableStateFlow(false)
    private val undoEvents = MutableSharedFlow<UndoEvent>(extraBufferCapacity = 4)

    /** One-tap fills the screen offers to undo. */
    val undo: SharedFlow<UndoEvent> = undoEvents.asSharedFlow()

    val uiState: StateFlow<HabitsUiState> = combine(
        habits.watch().flowOn(io),
        goals.watch().flowOn(io),
        dayStartHour,
        hideDone,
    ) { data, (goalList, _), startHour, hiding ->
        HabitBoard.build(data, goalList, PlanningDay.of(clock(), startHour), hiding)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HabitsUiState())

    /** Hides the habits done today, or shows them again; the rest stay where they are. */
    fun setHideDone(hide: Boolean) {
        hideDone.value = hide
    }

    /** The planning day check-ins land on. */
    fun today(): LocalDate = PlanningDay.of(clock(), dayStartHour.value)

    /** Adds a habit starting today when [id] is null, otherwise changes it. False when it isn't valid. */
    suspend fun save(id: String?, draft: HabitDraft): Boolean = withContext(io) {
        if (id == null) habits.add(draft.copy(startsOn = today())) != null else habits.update(id, draft)
    }

    /**
     * What the bottom bar's line says as a habit (docs/composer.md, "Adding on Wants, Habits and
     * Goals"): how often and how much from the line, starting today and showing on Today, everything
     * else as a new habit has it. Pure and fast, so it runs on every keystroke.
     */
    fun preview(line: String): HabitDraft {
        val read = QuickAddLines.readHabit(line)
        return HabitDraft(
            name = read.name,
            startsOn = today(),
            cadence = read.cadence,
            weekdays = read.weekdays,
            times = read.times,
            measure = read.measure,
            target = read.target,
            unit = read.unit,
            direction = read.direction,
            showOnToday = true,
        )
    }

    /**
     * Adds the habit the line says when a name is left; otherwise, or when it isn't valid, the habit
     * form opens with what was read.
     */
    suspend fun addLine(line: String): LineOutcome<HabitDraft> {
        val draft = preview(line)
        if (draft.name.isBlank()) return LineOutcome.OpenForm(draft)
        return if (save(null, draft)) LineOutcome.Added else LineOutcome.OpenForm(draft)
    }

    /**
     * A tap on the button: a check toggles, a count adds one, an amount fills to its target with an undo
     * on the snackbar (docs/habits.md, "One tap"). False when it asks for the value instead: a limit's
     * amount, or an amount with nothing left to fill.
     */
    suspend fun tap(id: String): Boolean = withContext(io) {
        val day = today()
        if (habits.tap(id, day)) return@withContext true
        val before = habits.fill(id, day) ?: return@withContext false
        val name = habits.find(id)?.name.orEmpty()
        undoEvents.tryEmit(UndoEvent(UndoEvent.Kind.FILLED, name) { write { habits.setValue(id, day, before) } })
        true
    }

    /** Adds [amount] to today's value. */
    fun checkIn(id: String, amount: Double) = write { habits.checkIn(id, today(), amount) }

    /** Clears today's value, for a check-in made by mistake. */
    fun clearToday(id: String) = write { habits.setValue(id, today(), 0.0) }

    /** Skips today's period (sick, travelling) or takes the skip back. */
    fun skip(id: String, skipped: Boolean) = write { habits.skip(id, today(), skipped) }

    /** Fails today's period (it won't happen: missed now, the streak ends) or takes the fail back. */
    fun fail(id: String, failed: Boolean) = write { habits.fail(id, today(), failed) }

    fun pause(id: String) = write { habits.pause(id, today()) }

    fun resume(id: String) = write { habits.resume(id, today()) }

    fun setArchived(id: String, archived: Boolean) = write { habits.setArchived(id, archived) }

    fun delete(id: String) = write { habits.delete(id) }

    private fun write(work: () -> Unit) {
        viewModelScope.launch(io) { work() }
    }
}
