package com.goalmaker.app.ui.lists

import com.goalmaker.app.application.planning.AreaItem
import com.goalmaker.app.application.planning.ListFilter
import com.goalmaker.app.application.planning.TagItem
import com.goalmaker.app.application.planning.PlanningLists
import com.goalmaker.app.application.planning.ProjectItem
import com.goalmaker.app.application.planning.TaskItem
import com.goalmaker.app.ui.goals.GoalRow
import com.goalmaker.app.ui.habits.HabitRow

/**
 * Everything the lists show. [lists] is null until the replica has been read once; [refreshing] is
 * true only while a sync the user pulled for runs. [areas] and [tagNames] feed the rows and the
 * composer's preview, and [projects] name a project item's chip (docs/lists.md); [reminded] marks
 * the tasks with a reminder waiting; [filter] is what the lists are narrowed to, from [tags] and [areas]. [weekGoals] are this week's goals for Today's
 * folded section, [habits] the habits on Today, with the streak [habitMilestones] reached. Today on the
 * phone shows its tasks or its habits by [segment]; [shownHabits] are the habits without the done ones
 * while [hideDoneHabits] is on, [habitsLeft] counts the ones still to do, and [habitsAllDone] shows the
 * all done card (contracts/vectors/habits.json, standings and allDone).
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
    val segment: TodaySegment = TodaySegment.TASKS,
    val hideDoneHabits: Boolean = false,
    val shownHabits: List<HabitRow> = emptyList(),
    val habitsLeft: Int = 0,
    val habitsAllDone: Boolean = false,
) {
    private val projectById by lazy { projects.associateBy(ProjectItem::id) }

    /** The project a task is an item of, while that project is still there: the chip its row wears. */
    fun projectOf(task: TaskItem): ProjectItem? = task.projectId?.let(projectById::get)
}
