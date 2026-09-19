package com.goalmaker.app.ui.task

import com.goalmaker.app.application.planning.AreaItem
import com.goalmaker.app.application.planning.GoalItem
import com.goalmaker.app.application.planning.StepItem
import com.goalmaker.app.application.planning.TagItem
import com.goalmaker.app.application.planning.TaskItem

/**
 * A task and everything its detail view offers. [task] is null once it was deleted, here or on the
 * other device; [loaded] tells that apart from not having read it yet. [goals] are the goals it can
 * serve.
 */
data class TaskUiState(
    val loaded: Boolean = false,
    val task: TaskItem? = null,
    val steps: List<StepItem> = emptyList(),
    val areas: List<AreaItem> = emptyList(),
    val tags: List<TagItem> = emptyList(),
    val taskTagIds: Set<String> = emptySet(),
    val goals: List<GoalItem> = emptyList(),
)
