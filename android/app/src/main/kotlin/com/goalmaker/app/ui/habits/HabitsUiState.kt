package com.goalmaker.app.ui.habits

import com.goalmaker.app.application.planning.GoalItem
import java.time.LocalDate

/**
 * The Habits screen: the [active] habits, the same habits in their [sections] (Every day, Weekly,
 * Limits, without the done ones while [hideDone] is on), the [summary] card, the [archived] ones, the
 * goals a habit can serve, and the streak [milestones] reached (a new one gets confetti).
 */
data class HabitsUiState(
    val loaded: Boolean = false,
    val today: LocalDate = LocalDate.MIN,
    val active: List<HabitRow> = emptyList(),
    val sections: List<HabitSection> = emptyList(),
    val summary: HabitSummary = HabitSummary(),
    val hideDone: Boolean = false,
    val archived: List<HabitRow> = emptyList(),
    val goals: List<GoalItem> = emptyList(),
    val milestones: Set<String> = emptySet(),
)
