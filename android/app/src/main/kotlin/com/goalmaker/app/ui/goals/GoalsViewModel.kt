package com.goalmaker.app.ui.goals

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.goalmaker.app.application.planning.GoalDraft
import com.goalmaker.app.application.planning.GoalHorizon
import com.goalmaker.app.application.planning.GoalList
import com.goalmaker.app.application.planning.GoalRules
import com.goalmaker.app.application.planning.HabitList
import com.goalmaker.app.application.planning.TaskList
import com.goalmaker.app.application.settings.SettingsStore
import com.goalmaker.app.domain.planning.PlanningDay
import com.goalmaker.app.domain.settings.GoalsView
import java.time.LocalDateTime
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * The Goals screen (docs/goals.md): rings for this year, month, week and today, then the ladder of their
 * goals and next week's for planning ahead. A ring filters to its horizon; picking a goal lights what it
 * feeds and what feeds it. [habits] serving a numeric goal add their check-ins. The [settings] say which
 * view this phone shows, the ladder or the plain list, and when the planning day starts. Disk work runs
 * on [io]; [clock] gives the time.
 */
class GoalsViewModel(
    private val goals: GoalList,
    tasks: TaskList,
    habits: HabitList,
    private val settings: SettingsStore,
    private val io: CoroutineDispatcher,
    private val clock: () -> LocalDateTime,
) : ViewModel() {
    private val focus = MutableStateFlow(Focus())

    val uiState: StateFlow<GoalsUiState> = combine(
        goals.watch().flowOn(io),
        tasks.watchAll().flowOn(io),
        habits.watch().flowOn(io),
        settings.dayStartHour,
        focus,
    ) { (all, entries), taskList, habitData, startHour, (filter, picked) ->
        GoalBoard.build(all, entries, taskList, PlanningDay.of(clock(), startHour), habitData, filter, picked)
    }.combine(settings.goalsView) { state, view -> state.copy(view = view) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), GoalsUiState())

    /** Shows the ladder or the plain list; this phone remembers it. The list has no chain, so a lit one goes out. */
    fun showView(view: GoalsView) {
        if (view == GoalsView.LIST) clearPick()
        settings.setGoalsView(view)
    }

    /** Shows only [horizon]'s rung, or every rung again when it was already the one shown. */
    fun filter(horizon: GoalHorizon) = focus.update { it.copy(filter = if (it.filter == horizon) null else horizon) }

    /** Lights the chain of the goal [id], or puts it out when [id] was already picked. */
    fun pick(id: String) = focus.update { it.copy(picked = if (it.picked == id) null else id) }

    /** Puts the lit chain out. */
    fun clearPick() = focus.update { it.copy(picked = null) }

    /** Adds a goal when [id] is null, otherwise changes it. False when the goal isn't valid. */
    suspend fun save(id: String?, draft: GoalDraft): Boolean = withContext(io) {
        if (id == null) goals.add(draft) != null else goals.update(id, draft)
    }

    /** Open, done or dropped (GoalRules). */
    fun setStatus(id: String, status: String) = write { goals.setStatus(id, status) }

    fun delete(id: String) = write { goals.delete(id) }

    /** Logs an amount on a numeric goal for today's planning day; a negative one takes some off. */
    fun logAmount(id: String, amount: Double) {
        val day = PlanningDay.of(clock(), settings.dayStartHour.value)
        write { goals.logAmount(id, day, amount) }
    }

    /**
     * The card's quick log: a done-or-not goal flips between done and open, a numeric goal gets its
     * latest amount again. False when there is nothing to repeat yet, so the screen asks for an amount.
     */
    fun quickLog(row: GoalRow): Boolean {
        val goal = row.goal
        when {
            goal.mode == GoalRules.MODE_DONE -> setStatus(goal.id, if (goal.status == GoalRules.DONE) GoalRules.OPEN else GoalRules.DONE)
            goal.mode == GoalRules.MODE_NUMBER && row.quickAmount != null -> logAmount(goal.id, row.quickAmount)
            else -> return false
        }
        return true
    }

    /** Copies the last period's goals into [section]'s, which has none yet: last week's into this one, this week's into next. */
    fun copyPrevious(section: GoalSection) = write { goals.copyPrevious(section.horizon, section.start) }

    private fun write(work: () -> Unit) {
        viewModelScope.launch(io) { work() }
    }

    // The horizon the rings filter to and the goal whose chain is lit; neither is saved.
    private data class Focus(val filter: GoalHorizon? = null, val picked: String? = null)
}
