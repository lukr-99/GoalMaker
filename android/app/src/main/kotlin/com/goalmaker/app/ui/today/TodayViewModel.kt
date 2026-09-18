package com.goalmaker.app.ui.today

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.goalmaker.app.application.planning.TaskList
import com.goalmaker.app.application.sync.SyncCoordinator
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Today (M1): the synced open tasks, the composer and the sync indicator. Disk work runs on [io]. */
class TodayViewModel(
    private val tasks: TaskList,
    private val sync: SyncCoordinator,
    private val io: CoroutineDispatcher,
) : ViewModel() {

    private val refreshing = MutableStateFlow(false)

    val uiState: StateFlow<TodayUiState> =
        combine(tasks.watchOpen().flowOn(io), sync.status, refreshing) { open, status, pulled ->
            TodayUiState(tasks = open, loaded = true, sync = status, refreshing = pulled)
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = TodayUiState(tasks = emptyList(), loaded = false, sync = sync.status.value, refreshing = false),
        )

    /** Adds a task; false when there is nothing to add, so the composer keeps its text. */
    fun add(title: String): Boolean {
        if (title.isBlank()) return false
        viewModelScope.launch(io) { tasks.add(title) }
        return true
    }

    fun setDone(id: String, done: Boolean) {
        viewModelScope.launch(io) { tasks.setDone(id, done) }
    }

    fun delete(id: String) {
        viewModelScope.launch(io) { tasks.delete(id) }
    }

    /** Pull to refresh: syncs right away instead of after the debounce. */
    fun refresh() {
        if (refreshing.value) return
        refreshing.value = true
        viewModelScope.launch {
            try {
                sync.syncNow()
            } finally {
                refreshing.value = false
            }
        }
    }
}
