package com.goalmaker.app.ui.calendar

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.goalmaker.app.application.planning.CalendarRules
import com.goalmaker.app.application.planning.ReminderList
import com.goalmaker.app.application.planning.TaskList
import com.goalmaker.app.application.settings.SettingsStore
import com.goalmaker.app.domain.planning.PlanningDay
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
 * The calendar (docs/calendar.md, spec story 68): a week or a month of planned tasks, deadlines and
 * reminders, with a day opening what it holds and a task moving to another day from there.
 */
class CalendarViewModel(
    private val tasks: TaskList,
    reminders: ReminderList,
    private val settings: SettingsStore,
    private val io: CoroutineDispatcher,
    private val clock: () -> LocalDateTime,
) : ViewModel() {
    private val view = MutableStateFlow(View(CalendarRules.MONTH, null, null))

    val uiState: StateFlow<CalendarUiState> = combine(
        tasks.watchAll().flowOn(io),
        reminders.watchAll().flowOn(io),
        view,
    ) { taskList, reminderList, showing ->
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
            ),
            selected = showing.selected,
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
}
