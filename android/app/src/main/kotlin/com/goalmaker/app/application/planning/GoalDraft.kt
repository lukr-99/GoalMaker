package com.goalmaker.app.application.planning

import java.time.LocalDate

/** What a new or edited goal says (docs/goals.md); [periodStart] is moved to its period's first day. */
data class GoalDraft(
    val title: String,
    val horizon: GoalHorizon,
    val periodStart: LocalDate,
    val mode: String = GoalRules.MODE_DONE,
    val emoji: String? = null,
    val parentId: String? = null,
    val target: Double? = null,
    val unit: String? = null,
)
