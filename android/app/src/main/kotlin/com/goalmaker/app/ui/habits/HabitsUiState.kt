package com.goalmaker.app.ui.habits

import com.goalmaker.app.application.planning.GoalItem
import java.time.LocalDate

/**
 * The Habits screen: the [active] habits and the [archived] ones, the goals a habit can serve, and the
 * streak [milestones] reached (a new one gets confetti).
 */
data class HabitsUiState(
    val loaded: Boolean = false,
    val today: LocalDate = LocalDate.MIN,
    val active: List<HabitRow> = emptyList(),
    val archived: List<HabitRow> = emptyList(),
    val goals: List<GoalItem> = emptyList(),
    val milestones: Set<String> = emptySet(),
)
