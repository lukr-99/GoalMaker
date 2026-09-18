package com.goalmaker.app.application.planning

import java.time.LocalDate

/** Every list the planner shows, computed together from the same tasks and planning day. */
data class PlanningLists(
    val today: LocalDate,
    val todaySections: TodaySections,
    val tomorrow: List<TaskItem>,
    val inbox: List<TaskItem>,
    val summary: DaySummary,
)
