package com.goalmaker.app.ui.task

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.goalmaker.app.application.planning.AreaList
import com.goalmaker.app.application.planning.GoalItem
import com.goalmaker.app.application.planning.GoalList
import com.goalmaker.app.application.planning.GoalRules
import com.goalmaker.app.application.planning.ProjectList
import com.goalmaker.app.application.planning.StepItem
import com.goalmaker.app.application.planning.StepList
import com.goalmaker.app.application.planning.TagItem
import com.goalmaker.app.application.planning.TagList
import com.goalmaker.app.application.planning.TaskItem
import com.goalmaker.app.application.planning.TaskList
import java.time.LocalDate
import java.time.LocalTime
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * One task's detail view (docs/archive.md): every field, its tags, its checklist, the goal it
 * serves and the project it is an item of, with the item's id (GM-12) to copy. Each change is
 * written at once and syncs like any other; disk work runs on [io], and [today] is the planning day
 * the goal picker counts from. Notes are written once typing pauses for [notesPause], or at once
 * when the field or the view is left.
 */
class TaskViewModel(
    private val taskId: String,
    private val tasks: TaskList,
    areas: AreaList,
    private val tags: TagList,
    private val steps: StepList,
    goals: GoalList,
    projects: ProjectList,
    private val io: CoroutineDispatcher,
    private val notesPause: Duration = NOTES_PAUSE,
    private val today: () -> LocalDate,
) : ViewModel() {
    // The notes typed and not yet written, null when there is nothing waiting.
    @Volatile private var notesDraft: String? = null
    private var notesTimer: Job? = null
    private val notesState = MutableStateFlow(NotesSave.IDLE)

    /** Whether the notes typed are written yet, for the quiet mark under the field. */
    val notesSave: StateFlow<NotesSave> = notesState.asStateFlow()

    private val tagging = combine(tags.watch().flowOn(io), tags.watchLinks().flowOn(io)) { tagList, links -> tagList to links[taskId].orEmpty() }
    // Combine takes five flows, so the goals and the projects travel together.
    private val filing = combine(goals.watch().flowOn(io), projects.watch().flowOn(io)) { (goalList, _), data -> goalList to data.projects }

    private val copyEvents = MutableSharedFlow<String>(extraBufferCapacity = 4)

    /** The item's id to put on the clipboard, with the usual confirmation. */
    val copies: SharedFlow<String> = copyEvents.asSharedFlow()

    val uiState: StateFlow<TaskUiState> = combine(
        tasks.watch(taskId).flowOn(io),
        steps.watch(taskId).flowOn(io),
        areas.watch().flowOn(io),
        tagging,
        filing,
    ) { task, stepList, areaList, (tagList, tagIds), (goalList, projectList) ->
        TaskUiState(
            loaded = true,
            task = task,
            steps = stepList,
            areas = areaList,
            tags = tagList,
            taskTagIds = tagIds,
            goals = goalChoices(goalList, task),
            projects = projectList,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TaskUiState())

    fun rename(title: String) = write { tasks.rename(taskId, title) }

    /**
     * Takes the notes as they are typed and writes them after a short pause in typing, so an edit
     * is never lost to a forgotten button.
     */
    fun editNotes(notes: String) {
        notesDraft = notes
        notesState.value = NotesSave.PENDING
        notesTimer?.cancel()
        notesTimer = viewModelScope.launch {
            delay(notesPause)
            saveNotes()
        }
    }

    /**
     * Writes the notes typed so far at once: when the field loses focus, the view closes or Back is
     * pressed. Nothing is written when they are what the task already holds. The write finishes even
     * when the view is on its way out.
     */
    fun saveNotes() {
        notesTimer?.cancel()
        notesTimer = null
        val notes = notesDraft ?: return
        notesDraft = null
        viewModelScope.launch(io + NonCancellable) {
            val stored = tasks.find(taskId)?.notes ?: return@launch
            if (stored != notes) tasks.setNotes(taskId, notes)
            // Typing again while this was written leaves it waiting.
            if (notesDraft == null) notesState.value = NotesSave.SAVED
        }
    }

    override fun onCleared() = saveNotes()

    fun schedule(day: LocalDate?, time: LocalTime?) = write { tasks.schedule(taskId, day, time) }

    fun setDeadline(day: LocalDate?) = write { tasks.setDeadline(taskId, day) }

    fun setArea(areaId: String?) = write { tasks.setArea(taskId, areaId) }

    fun setRecurrence(rule: String?) = write { tasks.setRecurrence(taskId, rule) }

    /** Links the task to the goal it serves, or to none. */
    fun setGoal(goalId: String?) = write { tasks.setGoal(taskId, goalId) }

    /**
     * Files the task as an item of a project, which lands it in that board's To do, or takes it out
     * of the one it was in, which leaves it a plain task (docs/projects.md).
     */
    fun setProject(projectId: String?) = write { tasks.setProject(taskId, projectId) }

    fun setDone(done: Boolean) = write { tasks.setDone(taskId, done) }

    /** Puts the item's id on the clipboard; a task without one has nothing to copy. */
    fun copyId() {
        uiState.value.itemId?.let(copyEvents::tryEmit)
    }

    fun delete() = write { tasks.delete(taskId) }

    /** Links the tag, or takes it off when it is already linked. */
    fun toggleTag(tag: TagItem) = write {
        val names = tags.forTask(taskId).map(TagItem::name)
        tasks.setTags(taskId, if (tag.name in names) names - tag.name else names + tag.name)
    }

    /** Links a tag by name, making it when it is new. */
    fun addTag(name: String) = write {
        if (name.isNotBlank()) tasks.setTags(taskId, tags.forTask(taskId).map(TagItem::name) + name.trim().removePrefix("#"))
    }

    fun addStep(title: String) = write { steps.add(taskId, title) }

    fun renameStep(id: String, title: String) = write { steps.rename(id, title) }

    fun toggleStep(step: StepItem) = write { steps.setDone(step.id, !step.done) }

    fun deleteStep(id: String) = write { steps.delete(id) }

    /** Moves a step one place up ([by] -1) or down ([by] 1). */
    fun moveStep(id: String, by: Int) = write {
        val index = steps.forTask(taskId).indexOfFirst { it.id == id }
        if (index >= 0) steps.move(id, index + by)
    }

    private fun write(work: () -> Unit) {
        viewModelScope.launch(io) { work() }
    }

    // The goals a task can serve: the open ones whose period hasn't ended, and the one it serves now.
    private fun goalChoices(all: List<GoalItem>, task: TaskItem?): List<GoalItem> {
        val day = today()
        return all.filter { goal ->
            goal.id == task?.goalId ||
                (goal.status == GoalRules.OPEN && !GoalRules.periodEnd(goal.horizon, goal.periodStart).isBefore(day))
        }
    }

    private companion object {
        /** How long typing has to pause before the notes are written. */
        val NOTES_PAUSE = 800.milliseconds
    }
}
