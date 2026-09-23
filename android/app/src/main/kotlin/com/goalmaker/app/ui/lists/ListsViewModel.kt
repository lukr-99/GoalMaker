package com.goalmaker.app.ui.lists

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.goalmaker.app.application.planning.AreaList
import com.goalmaker.app.application.planning.GoalList
import com.goalmaker.app.application.planning.HabitList
import com.goalmaker.app.application.planning.ListFilter
import com.goalmaker.app.application.planning.ListRules
import com.goalmaker.app.application.planning.ProjectList
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
import com.goalmaker.app.ui.goals.GoalBoard
import com.goalmaker.app.ui.habits.HabitBoard
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
 * (docs/composer.md), completing and deleting with undo, reminders (docs/reminders.md), today's habits
 * (docs/habits.md), this week's goals (docs/goals.md) and the sync indicator. Disk work runs on [io]; [clock] is the local time the planning day and the
 * composer read.
 */
class ListsViewModel(
    private val tasks: TaskList,
    areas: AreaList,
    private val tags: TagList,
    projects: ProjectList,
    goals: GoalList,
    private val habits: HabitList,
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

    private val rows = combine(
        areas.watch().flowOn(io),
        tags.watch().flowOn(io),
        reminded,
        projects.watch().flowOn(io).map { it.projects },
        ::RowContext,
    )

    // Today's habits for the ring row, and this week's goals with where they stand (habit check-ins
    // included) for Today's folded section (design spec, Today).
    private val goalsAndHabits = combine(goals.watch().flowOn(io), tasks.watchAll().flowOn(io), habits.watch().flowOn(io), settings.dayStartHour, minutes) {
            (all, entries), taskList, habitData, startHour, _ ->
        val day = PlanningDay.of(clock(), startHour)
        GoalBoard.thisWeek(all, entries, taskList, day, habitData) to HabitBoard.today(habitData, day)
    }

    val uiState: StateFlow<ListsUiState> = combine(
        lists,
        rows,
        refreshing,
        goalsAndHabits,
    ) { (planning, narrowed), context, pulled, (goalRows, habitRows) ->
        ListsUiState(
            lists = planning,
            refreshing = pulled,
            areas = context.areas,
            tagNames = context.tags.map { it.name },
            reminded = context.reminded,
            filter = narrowed,
            tags = context.tags,
            projects = context.projects,
            weekGoals = goalRows,
            habits = habitRows,
            habitMilestones = HabitBoard.milestones(habitRows),
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = ListsUiState(lists = null, refreshing = false),
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

    /** A tap on a habit's ring: a check toggles, a count adds one. False for an amount, which asks for the value. */
    suspend fun tapHabit(id: String): Boolean = withContext(io) { habits.tap(id, today()) }

    /** Adds [amount] to today's value of a habit. */
    fun checkIn(id: String, amount: Double) {
        viewModelScope.launch(io) { habits.checkIn(id, today(), amount) }
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
