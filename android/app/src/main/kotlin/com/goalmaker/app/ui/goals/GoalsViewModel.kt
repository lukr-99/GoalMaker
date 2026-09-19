package com.goalmaker.app.ui.goals

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.goalmaker.app.application.planning.GoalDraft
import com.goalmaker.app.application.planning.GoalList
import com.goalmaker.app.application.planning.TaskList
import com.goalmaker.app.domain.planning.PlanningDay
import java.time.LocalDateTime
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * The Goals screen (docs/goals.md): this year's, month's, week's and today's goals with their progress,
 * next week's for planning ahead, and the same goals as the cascade. Disk work runs on [io]; [clock] and
 * [dayStartHour] give the planning day.
 */
class GoalsViewModel(
    private val goals: GoalList,
    tasks: TaskList,
    private val dayStartHour: StateFlow<Int>,
    private val io: CoroutineDispatcher,
    private val clock: () -> LocalDateTime,
) : ViewModel() {
    private val tree = MutableStateFlow(false)

    val uiState: StateFlow<GoalsUiState> = combine(
        goals.watch().flowOn(io),
        tasks.watchAll().flowOn(io),
        dayStartHour,
        tree,
    ) { (all, entries), taskList, startHour, showTree ->
        GoalBoard.build(all, entries, taskList, PlanningDay.of(clock(), startHour), showTree)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), GoalsUiState())

    /** Shows the goals as the cascade instead of by period, or back. */
    fun showTree(show: Boolean) {
        tree.value = show
    }

    /** Adds a goal when [id] is null, otherwise changes it. False when the goal isn't valid. */
    suspend fun save(id: String?, draft: GoalDraft): Boolean = withContext(io) {
        if (id == null) goals.add(draft) != null else goals.update(id, draft)
    }

    /** Open, done or dropped (GoalRules). */
    fun setStatus(id: String, status: String) = write { goals.setStatus(id, status) }

    fun delete(id: String) = write { goals.delete(id) }

    /** Logs an amount on a numeric goal for today's planning day; a negative one takes some off. */
    fun logAmount(id: String, amount: Double) {
        val day = PlanningDay.of(clock(), dayStartHour.value)
        write { goals.logAmount(id, day, amount) }
    }

    /** Copies the last period's goals into [section]'s, which has none yet. */
    fun copyPrevious(section: GoalSection) = write { goals.copyPrevious(section.horizon, section.start) }

    private fun write(work: () -> Unit) {
        viewModelScope.launch(io) { work() }
    }
}
