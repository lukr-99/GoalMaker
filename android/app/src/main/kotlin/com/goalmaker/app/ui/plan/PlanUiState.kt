package com.goalmaker.app.ui.plan

import com.goalmaker.app.application.planning.AreaItem
import com.goalmaker.app.application.planning.PlanDecision
import com.goalmaker.app.application.planning.PlanRules
import com.goalmaker.app.application.planning.TaskItem
import java.time.LocalDate

/** A task step 1 asks about, with what the ritual shows for it now. */
data class ReviewItem(val task: TaskItem, val decision: PlanDecision)

data class PlanUiState(
    val loaded: Boolean = false,
    val step: PlanStep = PlanStep.TODAY,
    val today: LocalDate = LocalDate.MIN,
    val review: List<ReviewItem> = emptyList(),
    val tomorrow: List<TaskItem> = emptyList(),
    val inbox: List<TaskItem> = emptyList(),
    val priorities: Int = 0,
    val areas: List<AreaItem> = emptyList(),
    val tagNames: List<String> = emptyList(),
) {
    val undecided: Int get() = review.count { it.decision == PlanDecision.UNDECIDED }

    /** Whether another task can become a top priority (docs/plan-tomorrow.md: up to three). */
    val canPickPriority: Boolean get() = priorities < PlanRules.MAX_PRIORITIES

    fun decided(decision: PlanDecision): Int = review.count { it.decision == decision }
}
