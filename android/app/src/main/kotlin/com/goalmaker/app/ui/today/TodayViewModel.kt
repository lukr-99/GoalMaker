package com.goalmaker.app.ui.today

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.goalmaker.app.application.planning.AreaList
import com.goalmaker.app.application.planning.TagList
import com.goalmaker.app.application.planning.TaskList
import com.goalmaker.app.application.sync.SyncCoordinator
import com.goalmaker.app.domain.composer.ComposerDraft
import com.goalmaker.app.domain.composer.ComposerParser
import java.time.LocalDate
import java.time.LocalDateTime
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Today: the synced open tasks, the composer with its live preview (docs/composer.md) and the sync
 * indicator. Disk work runs on [io]; [clock] is the local time the composer reads dates against.
 */
class TodayViewModel(
    private val tasks: TaskList,
    areas: AreaList,
    tags: TagList,
    private val sync: SyncCoordinator,
    private val io: CoroutineDispatcher,
    private val clock: () -> LocalDateTime,
    private val rolloverHour: Int = ROLLOVER_HOUR,
) : ViewModel() {

    private val refreshing = MutableStateFlow(false)

    val uiState: StateFlow<TodayUiState> = combine(
        tasks.watchOpen().flowOn(io),
        areas.watch().flowOn(io),
        tags.watchNames().flowOn(io),
        sync.status,
        refreshing,
    ) { open, areaList, tagNames, status, pulled ->
        TodayUiState(tasks = open, loaded = true, sync = status, refreshing = pulled, areas = areaList, tagNames = tagNames)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = TodayUiState(tasks = emptyList(), loaded = false, sync = sync.status.value, refreshing = false),
    )

    /** What the composer's line says right now. Pure and fast, so it runs on every keystroke. */
    fun preview(line: String): ComposerDraft = ComposerParser.parse(line, clock(), rolloverHour)

    /** The planning day the preview calls "today". */
    fun today(): LocalDate = clock().minusHours(rolloverHour.toLong()).toLocalDate()

    /** Saves the draft; false when there's nothing to save yet, so the composer keeps its text. */
    fun submit(draft: ComposerDraft): Boolean {
        if (draft.command != null || draft.title.isBlank()) return false
        viewModelScope.launch(io) { tasks.add(draft) }
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

    private companion object {
        /** The planning day starts at 04:00 (spec, story 25); a setting arrives with M2-06. */
        const val ROLLOVER_HOUR = 4
    }
}
