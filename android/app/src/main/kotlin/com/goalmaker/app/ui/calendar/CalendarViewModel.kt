package com.goalmaker.app.ui.calendar

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.goalmaker.app.application.planning.AreaList
import com.goalmaker.app.application.planning.CalendarRules
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
import com.goalmaker.app.domain.planning.PlanningDay
import com.goalmaker.app.ui.habits.HabitBoard
import com.goalmaker.app.ui.lists.PlaceFilter
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
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
 * its tasks ticked off on that day and its habits checked in, skipped or failed there.
 */
class CalendarViewModel(
    private val tasks: TaskList,
    reminders: ReminderList,
    areas: AreaList,
    tags: TagList,
    projects: ProjectList,
    private val habits: HabitList,
    private val settings: SettingsStore,
    private val io: CoroutineDispatcher,
    private val clock: () -> LocalDateTime,
) : ViewModel() {
    private val view = MutableStateFlow(View(CalendarRules.MONTH, null, null))
    private val filter = PlaceFilter(areas, tags, io)

    private val data = combine(
        tasks.watchAll().flowOn(io),
        reminders.watchAll().flowOn(io),
        projects.watch().flowOn(io).map { it.projects },
        habits.watch().flowOn(io),
        ::Data,
    )

    val uiState: StateFlow<CalendarUiState> = combine(data, view, filter.choices) { (taskList, reminderList, projectList, habitData), showing, choices ->
        val areasOfProjects = projectList.associate { it.id to it.areaId }
        val today = today()
        val anchor = showing.anchor ?: today
        CalendarUiState(
            loaded = true,
            kind = showing.kind,
            anchor = anchor,
            today = today,
            days = CalendarRules.build(
                taskList,
                reminderList,
                CalendarRules.start(showing.kind, anchor),
                CalendarRules.end(showing.kind, anchor),
            ) { task -> choices.filter.keeps(task, choices.links, areasOfProjects) },
            selected = showing.selected,
            projects = projectList,
            filter = choices,
            // A day gone by, or today, can still be checked in; a day to come can't (docs/calendar.md).
            dayHabits = showing.selected?.takeUnless { it.isAfter(today) }?.let { HabitBoard.due(habitData, it, onDay = it != today) }.orEmpty(),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CalendarUiState())

    /** Switches between the week and the month view, keeping the day in sight. */
    fun show(kind: String) = view.update { it.copy(kind = kind) }

    /** The week or month before the one on show. */
    fun back() = view.update { it.copy(anchor = step(it, -1), selected = null) }

    /** The week or month after the one on show. */
    fun forward() = view.update { it.copy(anchor = step(it, 1), selected = null) }

    /** Back to the week or month holding today. */
    fun today(reset: Boolean) = view.update { it.copy(anchor = null, selected = null) }

    /** Opens a day, or closes it when it is already open. */
    fun open(day: LocalDate) = view.update { it.copy(selected = if (it.selected == day) null else day) }

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

    /** A tap on a habit's button for [day]: a check toggles, a count adds one. False for an amount, which asks for the value. */
    suspend fun tapHabit(id: String, day: LocalDate): Boolean = withContext(io) { habits.tap(id, day) }

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

    private fun today(): LocalDate = PlanningDay.of(clock(), settings.dayStartHour.value)

    private fun MutableStateFlow<View>.update(edit: (View) -> View) {
        value = edit(value)
    }

    private data class View(val kind: String, val anchor: LocalDate?, val selected: LocalDate?)

    private data class Data(
        val tasks: List<TaskItem>,
        val reminders: List<ReminderItem>,
        val projects: List<ProjectItem>,
        val habits: HabitData,
    )
}
