package com.goalmaker.app.ui.calendar

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.goalmaker.app.application.planning.AreaItem
import com.goalmaker.app.application.planning.AreaList
import com.goalmaker.app.application.planning.CalendarRules
import com.goalmaker.app.application.planning.EventDraft
import com.goalmaker.app.application.planning.EventItem
import com.goalmaker.app.application.planning.EventList
import com.goalmaker.app.application.planning.EventRules
import com.goalmaker.app.application.planning.HabitData
import com.goalmaker.app.application.planning.HabitList
import com.goalmaker.app.application.planning.ProjectItem
import com.goalmaker.app.application.planning.ReminderItem
import com.goalmaker.app.application.planning.TaskItem
import com.goalmaker.app.application.planning.ProjectList
import com.goalmaker.app.application.planning.ReminderList
import com.goalmaker.app.application.planning.TagList
import com.goalmaker.app.application.planning.TaskList
import com.goalmaker.app.application.settings.SettingsStore
import com.goalmaker.app.domain.composer.ComposerDraft
import com.goalmaker.app.domain.composer.ComposerParser
import com.goalmaker.app.domain.composer.SpanKind
import com.goalmaker.app.domain.settings.PickedDaysAdd
import com.goalmaker.app.domain.planning.PlanningDay
import com.goalmaker.app.ui.habits.HabitBoard
import com.goalmaker.app.ui.lists.PlaceFilter
import com.goalmaker.app.ui.lists.UndoEvent
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * The calendar (docs/calendar.md, spec story 68): a week or a month of planned tasks, deadlines and
 * reminders, with a day opening what it holds and a task moving to another day from there. The area
 * and tag filter narrows what the grid counts and the day lists, as it narrows the lists, and a project
 * item on the day wears its project's chip ([projects], docs/lists.md). A day gone by can be put right:
 * its tasks ticked off on that day and its habits checked in, skipped or failed there. Events draw as
 * bars across their days, narrowed by the area filter, and open in the event sheet, which saves and
 * deletes them (docs/calendar.md, Events).
 *
 * The bottom bar adds to the open day (today when none is open): a task by the composer's rules, or an
 * event whose title is the line. A long-press starts picking several days, and then the bar adds one
 * event from the first picked day to the last, or a copy of the task on each picked day; the choice
 * starts at the one used last time on this device. Every add can be taken back as a whole.
 */
class CalendarViewModel(
    private val tasks: TaskList,
    reminders: ReminderList,
    areas: AreaList,
    tags: TagList,
    projects: ProjectList,
    private val habits: HabitList,
    private val events: EventList,
    private val settings: SettingsStore,
    private val io: CoroutineDispatcher,
    private val clock: () -> LocalDateTime,
) : ViewModel() {
    private val view = MutableStateFlow(View(CalendarRules.MONTH, null, null))
    private val viewAndChoice = combine(view, settings.pickedDaysAdd, ::Pair)
    private val filter = PlaceFilter(areas, tags, io)
    private val undoEvents = MutableSharedFlow<UndoEvent>(extraBufferCapacity = 4)

    private val data = combine(
        tasks.watchAll().flowOn(io),
        reminders.watchAll().flowOn(io),
        projects.watch().flowOn(io).map { it.projects },
        habits.watch().flowOn(io),
        combine(events.watch().flowOn(io), areas.watch().flowOn(io), ::Pair),
        ::Data,
    )

    val uiState: StateFlow<CalendarUiState> = combine(data, viewAndChoice, filter.choices) { (taskList, reminderList, projectList, habitData, eventsAndAreas), (showing, lastChoice), choices ->
        val (eventList, areaList) = eventsAndAreas
        val areasOfProjects = projectList.associate { it.id to it.areaId }
        val today = today()
        val anchor = showing.anchor ?: today
        val start = CalendarRules.start(showing.kind, anchor)
        val end = CalendarRules.end(showing.kind, anchor)
        // Events have no tags, so the area filter keeps an area's events and a tag filter hides them all.
        val eventDays = EventRules.days(eventList, start, end, choices.filter::keeps)
        CalendarUiState(
            loaded = true,
            kind = showing.kind,
            anchor = anchor,
            today = today,
            days = CalendarRules.build(taskList, reminderList, start, end) { task ->
                choices.filter.keeps(task, choices.links, areasOfProjects)
            }.map { day -> day.copy(events = eventDays[day.day].orEmpty()) },
            selected = showing.selected,
            projects = projectList,
            filter = choices,
            // A day gone by, or today, can still be checked in; a day to come can't (docs/calendar.md).
            dayHabits = showing.selected?.takeUnless { it.isAfter(today) }?.let { HabitBoard.due(habitData, it, onDay = it != today) }.orEmpty(),
            bars = EventRules.bars(eventList, start, end, choices.filter::keeps),
            areas = areaList,
            picking = showing.picking,
            picked = showing.picked.toSet(),
            addDays = addDays(showing, today),
            addsEvent = addsEvent(showing, today, lastChoice),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CalendarUiState())

    /** Event deletions and what the bar added, which the screen offers to undo. */
    val undo: SharedFlow<UndoEvent> = undoEvents.asSharedFlow()

    /**
     * Saves the event sheet: a new event when [initial] is null, else the changes to it. False when the
     * draft is not one the server takes, so the sheet stays open.
     */
    suspend fun saveEvent(initial: EventItem?, draft: EventDraft): Boolean = withContext(io) {
        if (initial == null) events.add(draft) != null else events.update(initial.id, draft)
    }

    /** Deletes an event, with an undo on the snackbar. */
    fun deleteEvent(event: EventItem) {
        viewModelScope.launch(io) {
            if (events.delete(event.id)) {
                undoEvents.tryEmit(UndoEvent(UndoEvent.Kind.DELETED, event.title) { viewModelScope.launch(io) { events.restore(event.id) } })
            }
        }
    }

    /** Switches between the week and the month view, keeping the day in sight. */
    fun show(kind: String) = view.update { it.copy(kind = kind) }

    /** The week or month before the one on show. */
    fun back() = view.update { it.copy(anchor = step(it, -1), selected = null) }

    /** The week or month after the one on show. */
    fun forward() = view.update { it.copy(anchor = step(it, 1), selected = null) }

    /** Back to the week or month holding today. */
    fun today(reset: Boolean) = view.update { it.copy(anchor = null, selected = null) }

    /** Opens a day, or closes it when it is already open. While picking, a tap picks the day or unpicks it. */
    fun open(day: LocalDate) = view.update { showing ->
        when {
            !showing.picking -> showing.copy(selected = if (showing.selected == day) null else day)
            day in showing.picked -> {
                val left = showing.picked - day
                // Unpicking the last day leaves picking, with that day still open.
                if (left.isEmpty()) showing.leavePicking() else showing.copy(picked = left, selected = left.last())
            }
            else -> showing.copy(picked = showing.picked + day, selected = day)
        }
    }

    /**
     * A long-press on [day]: starts picking several days with it, or adds it while picking already. A
     * drag that follows picks a run from it ([pickRun]).
     */
    fun startPicking(day: LocalDate) = view.update { showing ->
        val picked = if (showing.picking) (showing.picked - day) + day else listOf(day)
        showing.copy(
            picking = true,
            picked = picked,
            runBase = picked,
            selected = day,
            choice = if (showing.picking) showing.choice else null,
        )
    }

    /** A drag from [from] to [to] after a long-press: every day between them is picked, on top of what was before. */
    fun pickRun(from: LocalDate, to: LocalDate) = view.update { showing ->
        if (!showing.picking) return@update showing
        val last = maxOf(from, to)
        val run = generateSequence(minOf(from, to)) { it.plusDays(1) }.takeWhile { !it.isAfter(last) }.toList()
        showing.copy(picked = (showing.runBase + run).distinct(), selected = to)
    }

    /** Leaves picking (Back or Done) and goes back to one day, the one open. */
    fun stopPicking() = view.update { it.leavePicking() }

    /**
     * The bar's switch: an event or a task. Across several picked days it is the choice between one
     * event and a task on each day, which the next add remembers for next time.
     */
    fun chooseEvent(event: Boolean) = view.update { showing ->
        if (addDays(showing, today()).size > 1) {
            showing.copy(choice = if (event) PickedDaysAdd.ONE_EVENT else PickedDaysAdd.TASK_ON_EACH)
        } else {
            showing.copy(event = event)
        }
    }

    /**
     * What the bar's line says as a task right now. Pure and fast, so it runs on every keystroke. Across
     * several picked days each copy is a plain task on its own day, so a day or repeat the line names
     * is read out of the title but left out of the task.
     */
    fun preview(line: String): ComposerDraft {
        val draft = ComposerParser.parse(line, clock(), settings.dayStartHour.value)
        if (addDays(view.value, today()).size <= 1) return draft
        return draft.copy(
            plannedDate = null,
            repeat = null,
            spans = draft.spans.filterNot { it.kind == SpanKind.DATE || it.kind == SpanKind.REPEAT },
        )
    }

    /** The planning day, which the bar's chips read dates against. */
    fun today(): LocalDate = PlanningDay.of(clock(), settings.dayStartHour.value)

    /**
     * Adds what the bar's line says to the days it adds to (docs/calendar.md): an event whose title is
     * the line, across the picked days, or a task, a copy on each picked day. A task on one day keeps a
     * day or repeat the line names, as Tomorrow's bar does; else it is planned for that day, at the time
     * the line names. False when there is nothing to add, so the bar keeps its text.
     */
    fun add(line: String): Boolean {
        val showing = view.value
        val today = today()
        val days = addDays(showing, today)
        val event = addsEvent(showing, today, settings.pickedDaysAdd.value)
        val added = if (event) addEvent(line.trim(), days) else addTasks(preview(line), days)
        if (added && days.size > 1) {
            val choice = if (event) PickedDaysAdd.ONE_EVENT else PickedDaysAdd.TASK_ON_EACH
            settings.setPickedDaysAdd(choice)
            view.update { it.copy(choice = choice) }
        }
        return added
    }

    private fun addEvent(title: String, days: List<LocalDate>): Boolean {
        if (title.isEmpty() || title.length > EventRules.MAX_TITLE) return false
        val span = EventRules.pickedSpan(days) ?: return false
        viewModelScope.launch(io) {
            val made = events.add(EventDraft(title, span.start, span.endInclusive)) ?: return@launch
            undoEvents.tryEmit(UndoEvent(UndoEvent.Kind.ADDED, made.title) { viewModelScope.launch(io) { events.delete(made.id) } })
        }
        return true
    }

    private fun addTasks(draft: ComposerDraft, days: List<LocalDate>): Boolean {
        if (draft.command != null || draft.title.isBlank() || days.isEmpty()) return false
        val named = draft.spans.any { it.kind == SpanKind.DATE || it.kind == SpanKind.REPEAT }
        val drafts = when {
            days.size == 1 && named -> listOf(draft)
            days.size == 1 -> listOf(draft.copy(plannedDate = days.single()))
            // Each copy is a plain task on its picked day; preview has already left out the line's own day and repeat.
            else -> days.map { draft.copy(plannedDate = it, repeat = null) }
        }
        viewModelScope.launch(io) {
            val made = drafts.mapNotNull { tasks.add(it) }
            if (made.isEmpty()) return@launch
            undoEvents.tryEmit(UndoEvent(UndoEvent.Kind.ADDED, made.first().title) { viewModelScope.launch(io) { made.forEach { tasks.delete(it.id) } } })
        }
        return true
    }

    /** Narrows the calendar to an area, or stops narrowing by area when [areaId] is null. */
    fun filterByArea(areaId: String?) = filter.byArea(areaId)

    /** Narrows the calendar to a tag, or stops narrowing by tag when [tagId] is null. */
    fun filterByTag(tagId: String?) = filter.byTag(tagId)

    /**
     * Ticks a task off on [day], or opens it again (docs/calendar.md): a day gone by counts it as done on
     * that day, so the stats and the archive put it there.
     */
    fun setDone(task: TaskItem, done: Boolean, day: LocalDate) {
        viewModelScope.launch(io) {
            if (done) tasks.finishOn(task.id, day, today(), ZoneId.systemDefault()) else tasks.setDone(task.id, false)
        }
    }

    /**
     * A tap on a habit's button for [day]: a check toggles, a count adds one, an amount fills to its target
     * with an undo on the snackbar (docs/habits.md, "One tap"). False when it asks for the value instead: a
     * limit's amount, or an amount with nothing left to fill.
     */
    suspend fun tapHabit(id: String, day: LocalDate): Boolean = withContext(io) {
        if (habits.tap(id, day)) return@withContext true
        val before = habits.fill(id, day) ?: return@withContext false
        val name = habits.find(id)?.name.orEmpty()
        undoEvents.tryEmit(UndoEvent(UndoEvent.Kind.FILLED, name) { viewModelScope.launch(io) { habits.setValue(id, day, before) } })
        true
    }

    /** Adds [amount] to [day]'s value of a habit. */
    fun checkInHabit(id: String, day: LocalDate, amount: Double) = write { habits.checkIn(id, day, amount) }

    /** Skips the habit's period holding [day], or takes the skip back. */
    fun skipHabit(id: String, day: LocalDate, skipped: Boolean) = write { habits.skip(id, day, skipped) }

    /** Fails the habit's period holding [day], or takes the fail back. */
    fun failHabit(id: String, day: LocalDate, failed: Boolean) = write { habits.fail(id, day, failed) }

    /** Clears [day]'s value of a habit, for a check-in made by mistake. */
    fun clearHabit(id: String, day: LocalDate) = write { habits.setValue(id, day, 0.0) }

    private fun write(work: () -> Unit) {
        viewModelScope.launch(io) { work() }
    }

    /** Moves a task to another day, the way Plan tomorrow does. */
    fun plan(taskId: String, day: LocalDate) {
        viewModelScope.launch(io) { tasks.plan(taskId, day) }
    }

    private fun step(showing: View, by: Long): LocalDate {
        val anchor = showing.anchor ?: today()
        return if (showing.kind == CalendarRules.WEEK) anchor.plusWeeks(by) else anchor.plusMonths(by)
    }

    private fun MutableStateFlow<View>.update(edit: (View) -> View) {
        value = edit(value)
    }

    // The days the bar adds to: the picked ones, else the open day, else today.
    private fun addDays(showing: View, today: LocalDate): List<LocalDate> =
        if (showing.picking) EventRules.pickedDays(showing.picked) else listOf(showing.selected ?: today)

    private fun addsEvent(showing: View, today: LocalDate, lastChoice: PickedDaysAdd): Boolean =
        if (addDays(showing, today).size > 1) (showing.choice ?: lastChoice) == PickedDaysAdd.ONE_EVENT else showing.event

    /**
     * What the calendar shows. While [picking], [picked] holds the days in the order they were picked,
     * and [runBase] what was picked before the drag going on now. [event] is the switch on one day, and
     * [choice] the choice across several, null until it changes after picking starts.
     */
    private data class View(
        val kind: String,
        val anchor: LocalDate?,
        val selected: LocalDate?,
        val picking: Boolean = false,
        val picked: List<LocalDate> = emptyList(),
        val runBase: List<LocalDate> = emptyList(),
        val event: Boolean = false,
        val choice: PickedDaysAdd? = null,
    ) {
        fun leavePicking() = copy(picking = false, picked = emptyList(), runBase = emptyList(), choice = null)
    }

    private data class Data(
        val tasks: List<TaskItem>,
        val reminders: List<ReminderItem>,
        val projects: List<ProjectItem>,
        val habits: HabitData,
        val eventsAndAreas: Pair<List<EventItem>, List<AreaItem>>,
    )
}
