package com.goalmaker.app.application.planning

import java.time.LocalDate

/** An amount logged by hand on a numeric goal ("+5 km"); a negative one corrects an earlier entry. */
data class GoalEntryItem(
    val id: String,
    val goalId: String,
    val day: LocalDate,
    val amount: Double,
    val deleted: Boolean = false,
)
