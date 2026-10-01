package com.goalmaker.app.ui.places

import com.goalmaker.app.application.planning.GoalEntryItem
import com.goalmaker.app.application.planning.GoalItem
import com.goalmaker.app.application.planning.HabitData
import com.goalmaker.app.application.planning.HabitPeriodState
import com.goalmaker.app.application.planning.ListRules
import com.goalmaker.app.application.planning.ProjectRules
import com.goalmaker.app.application.planning.ReviewItem
import com.goalmaker.app.application.planning.ReviewRules
import com.goalmaker.app.application.planning.StatsRules
import com.goalmaker.app.application.planning.TaskItem
import com.goalmaker.app.application.planning.TaskState
import com.goalmaker.app.application.planning.WantItem
import com.goalmaker.app.application.planning.WantRules
import com.goalmaker.app.application.planning.WantState
import com.goalmaker.app.ui.goals.GoalBoard
import com.goalmaker.app.ui.habits.HabitBoard
import java.time.LocalDate

/** The Places hub's live numbers, from the same rules the places themselves use. */
object PlacesBoard {
    private const val COMING_DAYS = 7L

    fun build(
        tasks: List<TaskItem>,
        habits: HabitData,
        goals: List<GoalItem>,
        entries: List<GoalEntryItem>,
        reviews: List<ReviewItem>,
        today: LocalDate,
        wants: List<WantItem> = emptyList(),
    ): PlacesDigest {
        val lists = ListRules.lists(tasks, today)
        val open = tasks.filter { it.state == TaskState.OPEN }
        // The Habits tile counts every habit due today, the ones kept off Today too.
        val habitRows = HabitBoard.due(habits, today)
        val goalRows = GoalBoard.thisWeek(goals, entries, tasks, today, habits)
        val items = open.filter { it.projectId != null }
        return PlacesDigest(
            todayDone = lists.summary.done,
            todayTotal = lists.summary.total,
            tomorrow = lists.tomorrow.size,
            inbox = lists.inbox.size,
            comingWeek = open.count { task ->
                task.plannedDate?.let { it.isAfter(today) && !it.isAfter(today.plusDays(COMING_DAYS)) } ?: false
            },
            habitsMet = habitRows.count { it.state == HabitPeriodState.MET },
            habitsDue = habitRows.size,
            goalsHit = goalRows.count { it.progress.hit },
            goalsTotal = goalRows.size,
            projectsOpen = items.size,
            projectsDoing = items.count { it.boardColumn == ProjectRules.DOING },
            letterWaiting = letterWaiting(reviews, today),
            doneThisWeek = StatsRules.weeks(tasks, today, count = 1).lastOrNull()?.done ?: 0,
            archived = tasks.count { it.state == TaskState.DONE },
            wantsReady = wants.count { WantRules.state(it, today) == WantState.READY },
            wantsCooling = wants.count { WantRules.state(it, today) == WantState.COOLING },
        )
    }

    /** Last week's review has a letter and the owner hasn't started the review yet. */
    private fun letterWaiting(reviews: List<ReviewItem>, today: LocalDate): Boolean {
        val lastWeek = today.minusDays(today.dayOfWeek.value - 1L).minusWeeks(1)
        val review = reviews.firstOrNull { !it.deleted && it.kind == ReviewRules.WEEKLY && it.periodStart == lastWeek } ?: return false
        val started = review.mood != null || review.energy != null || review.reflections.any { it.answer.isNotBlank() }
        return review.summary.isNotBlank() && !started
    }
}
