package com.goalmaker.app.ui.lists

import com.goalmaker.app.application.planning.AreaItem
import com.goalmaker.app.application.planning.ListFilter
import com.goalmaker.app.application.planning.TagItem
import com.goalmaker.app.application.planning.PlanningLists
import com.goalmaker.app.application.planning.ProjectItem
import com.goalmaker.app.ui.goals.GoalRow
import com.goalmaker.app.ui.habits.HabitRow

/**
 * Everything the lists show. [lists] is null until the replica has been read once; [refreshing] is
 * true only while a sync the user pulled for runs. [areas] and [tagNames] feed the rows and the
 * composer's preview; [reminded] marks the tasks with a reminder waiting; [filter] is what the
 * lists are narrowed to, from [tags] and [areas]. [weekGoals] are this week's goals for Today's
 * folded section, [habits] today's habits for its ring row, with the streak [habitMilestones] reached.
 */
data class ListsUiState(
    val lists: PlanningLists?,
    val refreshing: Boolean,
    val areas: List<AreaItem> = emptyList(),
    val tagNames: List<String> = emptyList(),
    val reminded: Set<String> = emptySet(),
    val filter: ListFilter = ListFilter.NONE,
    val tags: List<TagItem> = emptyList(),
    val projects: List<ProjectItem> = emptyList(),
    val weekGoals: List<GoalRow> = emptyList(),
    val habits: List<HabitRow> = emptyList(),
    val habitMilestones: Set<String> = emptySet(),
)
