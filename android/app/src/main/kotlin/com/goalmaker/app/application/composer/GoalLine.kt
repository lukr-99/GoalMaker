package com.goalmaker.app.application.composer

import com.goalmaker.app.application.planning.GoalHorizon
import java.time.LocalDate

/** What the bar on Goals read from a line; [mode] is GoalRules.MODE_DONE or MODE_NUMBER. */
data class GoalLine(
    val title: String,
    val horizon: GoalHorizon,
    val periodStart: LocalDate,
    val mode: String,
    val target: Double?,
    val unit: String?,
)
