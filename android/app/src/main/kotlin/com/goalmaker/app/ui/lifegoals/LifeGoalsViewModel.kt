package com.goalmaker.app.ui.lifegoals

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.goalmaker.app.application.planning.LifeGoalDraft
import com.goalmaker.app.application.planning.LifeGoalItem
import com.goalmaker.app.application.planning.LifeGoalList
import com.goalmaker.app.application.planning.LifeGoalPicture
import com.goalmaker.app.application.planning.LifeGoalPictures
import com.goalmaker.app.application.planning.LifeGoalRules
import com.goalmaker.app.domain.planning.PlanningDay
import java.time.LocalDate
import java.time.LocalDateTime
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * The Life goals place (docs/life-goals.md, M9-02): open life goals in the owner's order with their
 * pictures and time left, the closed ones below, and the editor's adds and removals of pictures.
 */
class LifeGoalsViewModel(
    private val lifeGoals: LifeGoalList,
    private val pictures: LifeGoalPictures,
    private val dayStartHour: StateFlow<Int>,
    private val io: CoroutineDispatcher,
    private val clock: () -> LocalDateTime,
) : ViewModel() {

    private val undoEvents = MutableSharedFlow<LifeGoalUndo>(extraBufferCapacity = 4)

    /** Ticks every hour so the time left moves on with the days. */
    private val hours = flow {
        while (true) {
            emit(Unit)
            delay(HOUR)
        }
    }

    val uiState: StateFlow<LifeGoalsUiState> = combine(
        lifeGoals.watch().flowOn(io),
        lifeGoals.watchPictures().flowOn(io),
        pictures.changes,
        dayStartHour,
        hours,
    ) { all, allPictures, version, startHour, _ ->
        val today = PlanningDay.of(clock(), startHour)
        val byGoal = allPictures.groupBy(LifeGoalPicture::lifeGoalId)
        val rows = all.map { goal -> LifeGoalRow(goal, LifeGoalRules.timeLeft(goal.by, today), byGoal[goal.id].orEmpty()) }
        LifeGoalsUiState(
            loaded = true,
            open = rows.filter { it.goal.status == LifeGoalRules.OPEN },
            closed = rows.filter { it.goal.status != LifeGoalRules.OPEN },
            pictureVersion = version,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LifeGoalsUiState())

    /** Achievements, drops and deletions the screen offers to undo. */
    val undo: SharedFlow<LifeGoalUndo> = undoEvents.asSharedFlow()

    /** The planning day the editor's "In 10 years" counts from. */
    fun today(): LocalDate = PlanningDay.of(clock(), dayStartHour.value)

    /** The bytes of picture [id], or null while this device waits for them. */
    suspend fun picture(id: String): ByteArray? = withContext(io) { pictures.read(id) }

    /**
     * Saves the editor: the life goal, then the pictures it added and removed. Null when the life goal
     * could not be saved (no title or no why).
     */
    suspend fun save(initial: LifeGoalItem?, draft: LifeGoalDraft, added: List<ShrunkPicture>, removed: Set<String>): LifeGoalItem? =
        withContext(io) {
            val goal = if (initial == null) lifeGoals.add(draft) else initial.takeIf { lifeGoals.update(it.id, draft) }
            goal?.also { saved ->
                removed.forEach(lifeGoals::removePicture)
                added.forEach { pictures.add(saved.id, it.jpeg, it.width, it.height) }
            }
        }

    fun achieve(goal: LifeGoalItem) = close(goal, LifeGoalUndo.Kind.ACHIEVED) { lifeGoals.achieve(goal.id) }

    fun drop(goal: LifeGoalItem) = close(goal, LifeGoalUndo.Kind.DROPPED) { lifeGoals.drop(goal.id) }

    fun reopen(goal: LifeGoalItem) {
        viewModelScope.launch(io) { lifeGoals.reopen(goal.id) }
    }

    fun delete(goal: LifeGoalItem) {
        viewModelScope.launch(io) {
            if (lifeGoals.delete(goal.id)) {
                undoEvents.tryEmit(LifeGoalUndo(LifeGoalUndo.Kind.DELETED, goal.title) { viewModelScope.launch(io) { lifeGoals.restore(goal.id) } })
            }
        }
    }

    /** Moves an open life goal one place up ([by] -1) or down ([by] 1). */
    fun move(goal: LifeGoalItem, by: Int) {
        val ids = uiState.value.open.map { it.goal.id }.toMutableList()
        val from = ids.indexOf(goal.id)
        val to = from + by
        if (from < 0 || to !in ids.indices) return
        ids.add(to, ids.removeAt(from))
        viewModelScope.launch(io) { lifeGoals.reorder(ids) }
    }

    private fun close(goal: LifeGoalItem, kind: LifeGoalUndo.Kind, change: () -> Boolean) {
        viewModelScope.launch(io) {
            if (change()) undoEvents.tryEmit(LifeGoalUndo(kind, goal.title) { viewModelScope.launch(io) { lifeGoals.reopen(goal.id) } })
        }
    }

    private companion object {
        const val HOUR = 3_600_000L
    }
}
