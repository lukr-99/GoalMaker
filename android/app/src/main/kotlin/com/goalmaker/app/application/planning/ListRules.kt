package com.goalmaker.app.application.planning

import java.time.LocalDate

/** Which open tasks each list shows, in what order (docs/lists.md, contracts/vectors/lists.json). */
object ListRules {
    private val byTime = compareBy<TaskItem, java.time.LocalTime?>(nullsLast()) { it.plannedTime }
        .thenBy(TaskItem::createdAt)
        .thenBy(TaskItem::id)
    private val byCreation = compareBy(TaskItem::createdAt).thenBy(TaskItem::id)

    fun lists(tasks: List<TaskItem>, today: LocalDate): PlanningLists {
        val live = tasks.filter { !it.deleted }
        val open = live.filter { it.state == TaskState.OPEN }
        val planned = open.filter { it.plannedDate == today }
        val tomorrow = today.plusDays(1)
        val counted = live.filter { it.plannedDate == today && it.state != TaskState.DROPPED }
        return PlanningLists(
            today = today,
            todaySections = TodaySections(
                priorities = planned.filter { it.topPriority }.sortedWith(byTime),
                scheduled = planned.filter { !it.topPriority && it.plannedTime != null }.sortedWith(byTime),
                more = planned.filter { !it.topPriority && it.plannedTime == null }.sortedWith(byCreation),
                overdue = open.filter { it.plannedDate?.isBefore(today) == true }
                    .sortedWith(compareBy<TaskItem> { it.plannedDate }.then(byTime)),
            ),
            tomorrow = open.filter { it.plannedDate == tomorrow }
                .sortedWith(compareByDescending<TaskItem> { it.topPriority }.then(byTime)),
            inbox = open.filter { it.plannedDate == null && it.areaId == null }.sortedWith(byCreation),
            summary = DaySummary(done = counted.count { it.state == TaskState.DONE }, total = counted.size),
        )
    }
}
