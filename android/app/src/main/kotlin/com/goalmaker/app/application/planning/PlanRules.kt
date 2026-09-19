package com.goalmaker.app.application.planning

import java.time.LocalDate
import java.time.LocalTime

/** The Plan tomorrow ritual's rules (docs/plan-tomorrow.md, contracts/vectors/plan.json). */
object PlanRules {
    /** The most top priorities the ritual lets tomorrow have. */
    const val MAX_PRIORITIES = 3

    /** The composer command that opens the ritual (docs/composer.md). */
    const val COMMAND = "plan"

    private val byTime = compareBy<TaskItem, LocalTime?>(nullsLast()) { it.plannedTime }
        .thenBy(TaskItem::createdAt)
        .thenBy(TaskItem::id)
    private val oldestFirst = compareBy<TaskItem> { it.plannedDate }.then(byTime)

    /** Step 1: open tasks planned for [today] or earlier, oldest day first. */
    fun review(tasks: List<TaskItem>, today: LocalDate): List<TaskItem> = tasks
        .filter { !it.deleted && it.state == TaskState.OPEN && it.plannedDate?.isAfter(today) == false }
        .sortedWith(oldestFirst)

    /** What the ritual shows for [task], read from its state so changes from elsewhere show up. */
    fun decision(task: TaskItem, today: LocalDate): PlanDecision {
        val day = task.plannedDate
        return when {
            task.state == TaskState.DONE -> PlanDecision.DONE
            task.state == TaskState.DROPPED -> PlanDecision.DROPPED
            day == null -> PlanDecision.UNPLANNED
            !day.isAfter(today) -> PlanDecision.UNDECIDED
            day == today.plusDays(1) -> PlanDecision.TOMORROW
            else -> PlanDecision.LATER
        }
    }

    /** Step 2: tomorrow's open tasks by time, so flagging a priority doesn't move a row. */
    fun tomorrow(tasks: List<TaskItem>, today: LocalDate): List<TaskItem> {
        val tomorrow = today.plusDays(1)
        return tasks.filter { !it.deleted && it.state == TaskState.OPEN && it.plannedDate == tomorrow }.sortedWith(byTime)
    }

    /** How many of tomorrow's open tasks are top priorities. */
    fun priorities(tasks: List<TaskItem>, today: LocalDate): Int {
        val tomorrow = today.plusDays(1)
        return tasks.count { !it.deleted && it.state == TaskState.OPEN && it.topPriority && it.plannedDate == tomorrow }
    }

    /**
     * How often a task was moved once it goes from [before] to [after] (tasks.moved_count,
     * docs/reviews.md): moving a planned task to another day counts, planning one that had no day
     * doesn't, and neither does taking its day away.
     */
    fun moves(before: LocalDate?, after: LocalDate?, count: Int): Int =
        if (before != null && after != null && before != after) count + 1 else count
}
