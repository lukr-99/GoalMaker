package com.goalmaker.app.ui.lists

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.goalmaker.app.application.planning.AreaList
import com.goalmaker.app.application.planning.ListFilter
import com.goalmaker.app.application.planning.ListRules
import com.goalmaker.app.application.planning.ReminderItem
import com.goalmaker.app.application.planning.ReminderService
import com.goalmaker.app.application.planning.ReminderState
import com.goalmaker.app.application.planning.TagList
import com.goalmaker.app.application.planning.TaskItem
import com.goalmaker.app.application.planning.TaskList
import com.goalmaker.app.application.settings.SettingsStore
import com.goalmaker.app.application.sync.SyncCoordinator
import com.goalmaker.app.domain.composer.ComposerDraft
import com.goalmaker.app.domain.composer.ComposerParser
import com.goalmaker.app.domain.planning.PlanningDay
import com.goalmaker.app.domain.planning.Snooze
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
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Today, Tomorrow and the Inbox (docs/lists.md), the composer with its live preview
 * (docs/composer.md), completing and deleting with undo, reminders (docs/reminders.md), and the
 * sync indicator. Disk work runs on [io]; [clock] is the local time the planning day and the
 * composer read.
 */
class ListsViewModel(
    private val tasks: TaskList,
    areas: AreaList,
    private val tags: TagList,
    private val settings: SettingsStore,
    private val reminders: ReminderService,
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

    // The filter the owner chose, kept while they switch lists (docs/lists.md); one whose area or tag
    // was deleted meanwhile falls away instead of hiding everything.
    private val chosenFilter = MutableStateFlow(ListFilter.NONE)
    private val filter = combine(chosenFilter, areas.watch().flowOn(io), tags.watch().flowOn(io)) { chosen, areaList, tagList ->
        ListFilter(
            areaId = chosen.areaId?.takeIf { id -> areaList.any { it.id == id && !it.archived } },
            tagId = chosen.tagId?.takeIf { id -> tagList.any { it.id == id } },
        )
    }

    private val lists = combine(tasks.watchAll().flowOn(io), tags.watchLinks().flowOn(io), filter, settings.dayStartHour, minutes) {
            all, links, narrowed, startHour, _ ->
        ListRules.lists(narrowed.apply(all, links), PlanningDay.of(clock(), startHour)) to narrowed
    }

    // The tasks with a reminder still to come, so a row can show it without reading the table again.
    private val reminded = reminders.watch().flowOn(io).map { all ->
        all.filter { it.state == ReminderState.PENDING || it.state == ReminderState.SNOOZED }
            .map(ReminderItem::taskId)
            .toSet()
    }

    private val rows = combine(areas.watch().flowOn(io), tags.watch().flowOn(io), reminded, ::RowContext)

    val uiState: StateFlow<ListsUiState> = combine(
        lists,
        rows,
        sync.status,
        refreshing,
    ) { (planning, narrowed), context, status, pulled ->
        ListsUiState(
            lists = planning,
            sync = status,
            refreshing = pulled,
            areas = context.areas,
            tagNames = context.tags.map { it.name },
            reminded = context.reminded,
            filter = narrowed,
            tags = context.tags,
        )
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

    /** Narrows every list to an area, or stops narrowing by area when [areaId] is null. */
    fun filterByArea(areaId: String?) = chosenFilter.update { it.copy(areaId = areaId) }

    /** Narrows every list to a tag, or stops narrowing by tag when [tagId] is null. */
    fun filterByTag(tagId: String?) = chosenFilter.update { it.copy(tagId = tagId) }

    /** The reminders already on a task, for the sheet that edits them. */
    suspend fun remindersOf(taskId: String): List<ReminderItem> = withContext(io) { reminders.on(taskId) }

    /** A reminder [minutes] before the task's planned time; 0 means when it starts. */
    fun remindBefore(taskId: String, minutes: Int) {
        viewModelScope.launch(io) { reminders.addBefore(taskId, minutes) }
    }

    /** A reminder at a time of its own, counted from now. */
    fun remindAt(taskId: String, at: LocalDateTime) {
        viewModelScope.launch(io) { reminders.addAt(taskId, at) }
    }

    fun removeReminder(reminderId: String) {
        viewModelScope.launch(io) { reminders.remove(reminderId) }
    }

    /** Where "tomorrow morning" lands from here (docs/reminders.md). */
    fun tomorrowMorning(): LocalDateTime = Snooze.TOMORROW_MORNING.target(clock(), settings.dayStartHour.value)

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
