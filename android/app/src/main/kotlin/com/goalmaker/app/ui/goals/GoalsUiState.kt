package com.goalmaker.app.ui.goals

import com.goalmaker.app.application.planning.GoalItem
import java.time.LocalDate

/**
 * The Goals screen: the current periods' [sections], the same goals as a [tree] (the cascade), which of
 * the two is shown, every goal for the parent pickers, and the ids of the shown goals that are [hits]
 * (a new one gets confetti).
 */
data class GoalsUiState(
    val loaded: Boolean = false,
    val today: LocalDate = LocalDate.MIN,
    val sections: List<GoalSection> = emptyList(),
    val tree: List<GoalRow> = emptyList(),
    val showTree: Boolean = false,
    val goals: List<GoalItem> = emptyList(),
    val hits: Set<String> = emptySet(),
)
