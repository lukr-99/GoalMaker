package com.goalmaker.app.ui.lists

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.goalmaker.app.application.planning.AreaItem
import com.goalmaker.app.application.planning.AreaList
import com.goalmaker.app.application.planning.EventDraft
import com.goalmaker.app.application.planning.EventItem
import com.goalmaker.app.application.planning.EventList
import com.goalmaker.app.application.planning.EventRules
import com.goalmaker.app.application.planning.GoalList
import com.goalmaker.app.application.planning.ReviewList
import com.goalmaker.app.application.planning.ReviewRules
import com.goalmaker.app.application.planning.HabitList
import com.goalmaker.app.application.planning.HabitRules
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
import com.goalmaker.app.ui.habits.HabitRow
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
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Today, Tomorrow and the Inbox (docs/lists.md), the composer with its live preview
 * (docs/composer.md), completing and deleting with undo, reminders (docs/reminders.md), Today's switch
 * between its tasks and its habits with Hide done and the all done card (docs/habits.md), this week's goals (docs/goals.md), the
 * calendar events going on today (docs/calendar.md) and the sync indicator. Disk work runs on [io]; [clock] is the local time the planning day and the
 * composer read.
 */
class ListsViewModel(
    private val tasks: TaskList,
    areas: AreaList,
    private val tags: TagList,
    projects: ProjectList,
    goals: GoalList,
    reviews: ReviewList,
    private val habits: HabitList,
    private val events: EventList,
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
    private val filter = PlaceFilter(areas, tags, io)

    // A project item without an area of its own counts as being in its project's area.
    private val projectAreas = projects.watch().flowOn(io).map { data -> data.projects.associate { it.id to it.areaId } }

    private val lists = combine(tasks.watchAll().flowOn(io), filter.choices, projectAreas, settings.dayStartHour, minutes) {
            all, choices, areasOfProjects, startHour, _ ->
        val narrowed = choices.filter
        ListRules.lists(narrowed.apply(all, choices.links, areasOfProjects), PlanningDay.of(clock(), startHour)) to narrowed
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

    // Which half of Today shows and whether its done habits hide: kept while the app runs, so Today
    // comes back the way it was left, and starts on the tasks after a cold start.
    private val segment = MutableStateFlow(TodaySegment.TASKS)
    private val hideDoneHabits = MutableStateFlow(false)

    // The January nudge (docs/reviews.md): last year's review and this year's goals, until both are
    // done or the owner says Not now for the year.
    private val newYear = combine(goals.watch().flowOn(io), reviews.watch().flowOn(io), settings.newYearDismissed, settings.dayStartHour, minutes) {
            (all, _), reviewList, dismissed, startHour, _ ->
        ReviewRules.newYearFor(PlanningDay.of(clock(), startHour), all, reviewList, dismissed)
    }
    private val habitView = combine(segment, hideDoneHabits, newYear, ::Triple)

    // The events today falls inside, narrowed by the lists' area filter like the tasks: an area keeps
    // its events, and a tag hides them all, since events have no tags (docs/calendar.md).
    private val ongoing = combine(events.watch().flowOn(io), filter.choices, settings.dayStartHour, minutes) { all, choices, startHour, _ ->
        EventRules.ongoing(all.filter(choices.filter::keeps), PlanningDay.of(clock(), startHour))
    }
    private val todayView = combine(habitView, ongoing, ::Pair)

    val uiState: StateFlow<ListsUiState> = combine(
        lists,
        rows,
        refreshing,
        goalsAndHabits,
        todayView,
    ) { (planning, narrowed), context, pulled, (goalRows, habitRows), (habitShown, going) ->
        val (shownSegment, hiding, nudge) = habitShown
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
            segment = shownSegment,
            hideDoneHabits = hiding,
            shownHabits = HabitBoard.shown(habitRows, hiding),
            habitsLeft = habitRows.count(HabitRow::left),
            habitsAllDone = HabitRules.allDone(habitRows.map(HabitRow::standing)),
            newYear = nudge,
            ongoing = going,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = ListsUiState(lists = null, refreshing = false),
    )

    /** Completions and deletions the screen offers to undo. */
    val undo: SharedFlow<UndoEvent> = undoEvents.asSharedFlow()

    /** Puts the January nudge away until next January (docs/reviews.md). */
    fun dismissNewYear() {
        uiState.value.newYear?.let { settings.setNewYearDismissed(it.year) }
    }

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

    /**
     * Saves the new task form (docs/composer.md, the plus on Today, Tomorrow and the Inbox): the title
     * as typed, with no shortcuts read from it, the day picked, an area by its name, top priority and
     * the notes. False when the title is blank, so the form stays open.
     */
    fun addTask(title: String, day: NewTaskDay, area: AreaItem?, topPriority: Boolean, notes: String): Boolean {
        if (title.isBlank()) return false
        val draft = ComposerDraft(
            title = title.trim(),
            plannedDate = when (day) {
                NewTaskDay.TODAY -> today()
                NewTaskDay.TOMORROW -> today().plusDays(1)
                NewTaskDay.NO_DAY -> null
            },
            area = area?.name,
            topPriority = topPriority,
        )
        viewModelScope.launch(io) { tasks.add(draft, notes.trim()) }
        return true
    }

    /** Saves the event sheet opened from Today's line. False when the draft is not one the server takes. */
    suspend fun saveEvent(event: EventItem, draft: EventDraft): Boolean = withContext(io) { events.update(event.id, draft) }

    /** Deletes an event from its sheet, with an undo on the snackbar. */
    fun deleteEvent(event: EventItem) {
        viewModelScope.launch(io) { events.delete(event.id) }
        undoEvents.tryEmit(UndoEvent(UndoEvent.Kind.DELETED, event.title) { viewModelScope.launch(io) { events.restore(event.id) } })
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

    /** Skips a habit's period holding today (sick, travelling) or takes the skip back; the streak stays. */
    fun skipHabit(id: String, skipped: Boolean) {
        viewModelScope.launch(io) { habits.skip(id, today(), skipped) }
    }

    /** Fails a habit's period holding today (it won't happen: missed now, the streak ends) or takes the fail back. */
    fun failHabit(id: String, failed: Boolean) {
        viewModelScope.launch(io) { habits.fail(id, today(), failed) }
    }

    /** Clears today's value of a habit, for a check-in made by mistake. */
    fun clearHabit(id: String) {
        viewModelScope.launch(io) { habits.setValue(id, today(), 0.0) }
    }

    /** Pauses a habit from today; it leaves Today until it resumes on the Habits screen. */
    fun pauseHabit(id: String) {
        viewModelScope.launch(io) { habits.pause(id, today()) }
    }

    /** Shows Today's tasks or its habits. */
    fun showSegment(shown: TodaySegment) {
        segment.value = shown
    }

    /** Hides the habits done today from Today's habits, or shows them again. */
    fun setHideDoneHabits(hide: Boolean) {
        hideDoneHabits.value = hide
    }

    /** Narrows every list to an area, or stops narrowing by area when [areaId] is null. */
    fun filterByArea(areaId: String?) = filter.byArea(areaId)

    /** Narrows every list to a tag, or stops narrowing by tag when [tagId] is null. */
    fun filterByTag(tagId: String?) = filter.byTag(tagId)

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
