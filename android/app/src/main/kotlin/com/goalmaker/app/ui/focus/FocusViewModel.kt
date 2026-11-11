package com.goalmaker.app.ui.focus

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.goalmaker.app.application.planning.AreaItem
import com.goalmaker.app.application.planning.AreaList
import com.goalmaker.app.application.planning.TaskItem
import com.goalmaker.app.application.planning.TaskList
import com.goalmaker.app.application.planning.TaskState
import com.goalmaker.app.domain.planning.FocusBoardRules
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class FocusViewModel(
    private val tasks: TaskList,
    private val areas: AreaList,
    private val io: kotlinx.coroutines.CoroutineDispatcher,
) : ViewModel() {

    private val showAddSheet = MutableStateFlow(false)

    val uiState = combine(
        tasks.watchAll(),
        areas.watch(),
        showAddSheet,
    ) { all, allAreas, addSheet ->
        val board = FocusBoardRules.board(all)
        val onBoard = board.map(TaskItem::id).toSet()
        FocusUiState(
            items = board,
            areas = allAreas.filterNot(AreaItem::archived),
            candidates = all.filter { !it.deleted && it.state == TaskState.OPEN && it.id !in onBoard }
                .sortedWith(compareBy({ it.plannedDate?.toString() ?: "9999" }, { it.title.lowercase() })),
            showAddSheet = addSheet,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), FocusUiState())

    fun complete(id: String) {
        viewModelScope.launch(io) { tasks.setDone(id, true) }
    }

    fun removeFromBoard(id: String) {
        viewModelScope.launch(io) { tasks.setFocusPosition(id, null) }
    }

    /**
     * Moves the task at [fromIndex] to just before the item at [toIndex] in the current board
     * order. Called after a drag gesture settles.
     */
    fun move(fromIndex: Int, toIndex: Int) {
        val current = uiState.value.items
        if (fromIndex == toIndex || fromIndex !in current.indices) return
        val task = current[fromIndex]
        val adjusted = current.toMutableList().apply { removeAt(fromIndex) }
        val insertAt = if (toIndex > fromIndex) toIndex - 1 else toIndex
        val before = adjusted.getOrNull(insertAt - 1)
        val after = adjusted.getOrNull(insertAt)
        val newPos = FocusBoardRules.between(before, after)
        viewModelScope.launch(io) { tasks.setFocusPosition(task.id, newPos) }
    }

    fun addToBoard(task: TaskItem) {
        val newPos = FocusBoardRules.appendPosition(uiState.value.items + listOf(task))
        viewModelScope.launch(io) { tasks.setFocusPosition(task.id, newPos) }
        showAddSheet.update { false }
    }

    fun openAddSheet() { showAddSheet.update { true } }

    fun dismissAddSheet() { showAddSheet.update { false } }
}
