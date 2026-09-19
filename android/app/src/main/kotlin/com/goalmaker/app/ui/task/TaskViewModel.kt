package com.goalmaker.app.ui.task

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.goalmaker.app.application.planning.AreaList
import com.goalmaker.app.application.planning.GoalItem
import com.goalmaker.app.application.planning.GoalList
import com.goalmaker.app.application.planning.GoalRules
import com.goalmaker.app.application.planning.StepItem
import com.goalmaker.app.application.planning.StepList
import com.goalmaker.app.application.planning.TagItem
import com.goalmaker.app.application.planning.TagList
import com.goalmaker.app.application.planning.TaskItem
import com.goalmaker.app.application.planning.TaskList
import java.time.LocalDate
import java.time.LocalTime
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * One task's detail view (docs/archive.md): every field, its tags, its checklist and the goal it
 * serves. Each change is written at once and syncs like any other; disk work runs on [io], and
 * [today] is the planning day the goal picker counts from.
 */
class TaskViewModel(
    private val taskId: String,
    private val tasks: TaskList,
    areas: AreaList,
    private val tags: TagList,
    private val steps: StepList,
    goals: GoalList,
    private val io: CoroutineDispatcher,
    private val today: () -> LocalDate,
) : ViewModel() {
    private val tagging = combine(tags.watch().flowOn(io), tags.watchLinks().flowOn(io)) { tagList, links -> tagList to links[taskId].orEmpty() }

    val uiState: StateFlow<TaskUiState> = combine(
        tasks.watch(taskId).flowOn(io),
        steps.watch(taskId).flowOn(io),
        areas.watch().flowOn(io),
        tagging,
        goals.watch().flowOn(io),
    ) { task, stepList, areaList, (tagList, tagIds), (goalList, _) ->
        TaskUiState(
            loaded = true,
            task = task,
            steps = stepList,
            areas = areaList,
            tags = tagList,
            taskTagIds = tagIds,
            goals = goalChoices(goalList, task),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TaskUiState())

    fun rename(title: String) = write { tasks.rename(taskId, title) }

    fun setNotes(notes: String) = write { tasks.setNotes(taskId, notes) }

    fun schedule(day: LocalDate?, time: LocalTime?) = write { tasks.schedule(taskId, day, time) }

    fun setDeadline(day: LocalDate?) = write { tasks.setDeadline(taskId, day) }

    fun setArea(areaId: String?) = write { tasks.setArea(taskId, areaId) }

    fun setRecurrence(rule: String?) = write { tasks.setRecurrence(taskId, rule) }

    /** Links the task to the goal it serves, or to none. */
    fun setGoal(goalId: String?) = write { tasks.setGoal(taskId, goalId) }

    fun setDone(done: Boolean) = write { tasks.setDone(taskId, done) }

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
}
