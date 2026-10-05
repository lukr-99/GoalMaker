package com.goalmaker.app.application.composer

import com.goalmaker.app.application.planning.HabitRules

/**
 * What the bar on Habits read from a line. [cadence] and [measure] use HabitRules' ids; [weekdays] is
 * the Monday-first bit mask. [direction] is HabitRules.AT_MOST when the line sets a limit.
 */
data class HabitLine(
    val name: String,
    val cadence: String,
    val weekdays: Int?,
    val times: Int?,
    val measure: String,
    val target: Double?,
    val unit: String?,
    val direction: String,
)
