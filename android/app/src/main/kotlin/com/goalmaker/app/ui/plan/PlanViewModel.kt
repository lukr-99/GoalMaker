package com.goalmaker.app.ui.plan

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.goalmaker.app.application.planning.AreaList
import com.goalmaker.app.application.planning.ListRules
import com.goalmaker.app.application.planning.PlanDecision
import com.goalmaker.app.application.planning.PlanRules
import com.goalmaker.app.application.planning.ProjectList
import com.goalmaker.app.application.planning.TagList
import com.goalmaker.app.application.planning.TaskItem
import com.goalmaker.app.application.planning.TaskList
import com.goalmaker.app.application.settings.SettingsStore
import com.goalmaker.app.domain.composer.ComposerDraft
import com.goalmaker.app.domain.composer.ComposerParser
import com.goalmaker.app.domain.planning.PlanningDay
import java.time.LocalDate
import java.time.LocalDateTime
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * The Plan tomorrow ritual (docs/plan-tomorrow.md): decide on what's left from today, then set up
 * tomorrow and its top priorities. Decisions are saved at once; what each task shows is read back
 * from its state, so changes from another device show up too.
 */
class PlanViewModel(
    private val tasks: TaskList,
    areas: AreaList,
    tags: TagList,
    projects: ProjectList,
    private val settings: SettingsStore,
    private val io: CoroutineDispatcher,
    private val clock: () -> LocalDateTime,
    /** Called once when the ritual reaches its end, with the planning day it ran on. */
    private val onFinished: (LocalDate) -> Unit = {},
) : ViewModel() {

    /** The planning day, read once so the ritual's today and tomorrow stay put. */
    val today: LocalDate = PlanningDay.of(clock(), settings.dayStartHour.value)
    private val tomorrow: LocalDate = today.plusDays(1)
    private val step = MutableStateFlow(PlanStep.TODAY)

    // Every task step 1 has asked about, in the order it first appeared, so decided rows stay put.
    private val reviewed = LinkedHashSet<String>()

    val uiState: StateFlow<PlanUiState> = combine(
        tasks.watchAll().flowOn(io),
        areas.watch().flowOn(io),
        tags.watchNames().flowOn(io),
        projects.watch().flowOn(io).map { it.projects },
        step,
    ) { all, areaList, tagNames, projectList, current ->
        PlanRules.review(all, today).forEach { reviewed += it.id }
        val byId = all.associateBy(TaskItem::id)
        PlanUiState(
            loaded = true,
            step = current,
            today = today,
            review = reviewed.mapNotNull(byId::get).map { ReviewItem(it, PlanRules.decision(it, today)) },
            tomorrow = PlanRules.tomorrow(all, today),
            inbox = ListRules.lists(all, today).inbox,
            priorities = PlanRules.priorities(all, today),
            areas = areaList,
            tagNames = tagNames,
            projects = projectList,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PlanUiState(today = today))

    fun decide(task: TaskItem, decision: PlanDecision, day: LocalDate? = null) {
        viewModelScope.launch(io) {
            when (decision) {
                PlanDecision.TOMORROW -> tasks.plan(task.id, tomorrow)
                PlanDecision.LATER -> day?.let { tasks.plan(task.id, it) }
                PlanDecision.DONE -> tasks.setDone(task.id, true)
                PlanDecision.DROPPED -> tasks.drop(task.id)
                PlanDecision.UNDECIDED, PlanDecision.UNPLANNED -> Unit
            }
        }
    }

    /** Flags or clears a top priority; flagging stops at three (docs/plan-tomorrow.md). */
    fun togglePriority(task: TaskItem) {
        if (!task.topPriority && !uiState.value.canPickPriority) return
        viewModelScope.launch(io) { tasks.setTopPriority(task.id, !task.topPriority) }
    }

    /** Brings an Inbox task into tomorrow. */
    fun planForTomorrow(task: TaskItem) {
        viewModelScope.launch(io) { tasks.plan(task.id, tomorrow) }
    }

    fun preview(line: String): ComposerDraft = ComposerParser.parse(line, clock(), settings.dayStartHour.value)

    /**
     * Saves a line from step 2's composer, on tomorrow unless it names a day. `/plan` starts the
     * ritual over at step 1. False when there's nothing to save, so the composer keeps its text.
     */
    fun submit(draft: ComposerDraft): Boolean {
        val command = draft.command
        if (command != null) {
            if (command.name != PlanRules.COMMAND) return false
            step.value = PlanStep.TODAY
            return true
        }
        if (draft.title.isBlank()) return false
        val placed = if (draft.plannedDate == null) draft.copy(plannedDate = tomorrow) else draft
        viewModelScope.launch(io) { tasks.add(placed) }
        return true
    }

    fun next() {
        val before = step.value
        step.value = when (before) {
            PlanStep.TODAY -> if (uiState.value.undecided == 0) PlanStep.TOMORROW else PlanStep.TODAY
            PlanStep.TOMORROW, PlanStep.DONE -> PlanStep.DONE
        }
        // Reaching the end counts as the day's run, which quiets the evening reminder everywhere.
        if (before == PlanStep.TOMORROW) onFinished(today)
    }

    /** Back from step 2 goes to step 1; false where back should leave the ritual. */
    fun back(): Boolean {
        if (step.value != PlanStep.TOMORROW) return false
        step.value = PlanStep.TODAY
        return true
    }
}
