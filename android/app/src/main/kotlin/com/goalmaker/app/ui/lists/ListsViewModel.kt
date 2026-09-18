package com.goalmaker.app.ui.lists

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.goalmaker.app.application.planning.AreaList
import com.goalmaker.app.application.planning.ListRules
import com.goalmaker.app.application.planning.TagList
import com.goalmaker.app.application.planning.TaskItem
import com.goalmaker.app.application.planning.TaskList
import com.goalmaker.app.application.settings.SettingsStore
import com.goalmaker.app.application.sync.SyncCoordinator
import com.goalmaker.app.domain.composer.ComposerDraft
import com.goalmaker.app.domain.composer.ComposerParser
import com.goalmaker.app.domain.planning.PlanningDay
import java.time.LocalDate
import java.time.LocalDateTime
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Today, Tomorrow and the Inbox (docs/lists.md), the composer with its live preview
 * (docs/composer.md), completing and deleting with undo, and the sync indicator. Disk work runs on
 * [io]; [clock] is the local time the planning day and the composer read.
 */
class ListsViewModel(
    private val tasks: TaskList,
    areas: AreaList,
    tags: TagList,
    private val settings: SettingsStore,
    private val sync: SyncCoordinator,
    private val io: CoroutineDispatcher,
    private val clock: () -> LocalDateTime,
) : ViewModel() {

    private val refreshing = MutableStateFlow(false)
    private val undoEvents = MutableSharedFlow<UndoEvent>(extraBufferCapacity = 4)

    /** Ticks every minute so the lists move on when the planning day does. */
    private val minutes = flow {
        while (true) {
            emit(Unit)
            delay(MINUTE)
        }
    }

    private val lists = combine(tasks.watchAll().flowOn(io), settings.dayStartHour, minutes) { all, startHour, _ ->
        ListRules.lists(all, PlanningDay.of(clock(), startHour))
    }

    val uiState: StateFlow<ListsUiState> = combine(
        lists,
        areas.watch().flowOn(io),
        tags.watchNames().flowOn(io),
        sync.status,
        refreshing,
    ) { planning, areaList, tagNames, status, pulled ->
        ListsUiState(lists = planning, sync = status, refreshing = pulled, areas = areaList, tagNames = tagNames)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = ListsUiState(lists = null, sync = sync.status.value, refreshing = false),
    )

    /** Completions and deletions the screen offers to undo. */
    val undo: SharedFlow<UndoEvent> = undoEvents.asSharedFlow()

    /** The planning day the lists and the preview call "today". */
    fun today(): LocalDate = PlanningDay.of(clock(), settings.dayStartHour.value)

    /** What the composer's line says right now. Pure and fast, so it runs on every keystroke. */
    fun preview(line: String): ComposerDraft = ComposerParser.parse(line, clock(), settings.dayStartHour.value)

    /**
     * Saves the draft; the list it was typed on supplies the day when the line names none
     * (docs/composer.md). False when there's nothing to save, so the composer keeps its text.
     */
    fun submit(draft: ComposerDraft, tab: ListTab): Boolean {
        if (draft.command != null || draft.title.isBlank()) return false
        val day = when (tab) {
            ListTab.TODAY -> today()
            ListTab.TOMORROW -> today().plusDays(1)
            ListTab.INBOX -> null
        }
        val placed = if (draft.plannedDate == null && day != null) draft.copy(plannedDate = day) else draft
        viewModelScope.launch(io) { tasks.add(placed) }
        return true
    }

    fun complete(task: TaskItem) {
        viewModelScope.launch(io) { tasks.setDone(task.id, true) }
        undoEvents.tryEmit(UndoEvent(UndoEvent.Kind.DONE, task.title) { viewModelScope.launch(io) { tasks.setDone(task.id, false) } })
    }

    fun delete(task: TaskItem) {
        viewModelScope.launch(io) { tasks.delete(task.id) }
        undoEvents.tryEmit(UndoEvent(UndoEvent.Kind.DELETED, task.title) { viewModelScope.launch(io) { tasks.restore(task.id) } })
    }

    /** Pull to refresh or a tap on the sync indicator: syncs right away instead of after the debounce. */
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
        const val MINUTE = 60_000L
    }
}
