package com.goalmaker.app.application.composer

/**
 * What the bar on Habits read from a line. [cadence] and [measure] use HabitRules' ids; [weekdays] is
 * the Monday-first bit mask.
 */
data class HabitLine(
    val name: String,
    val cadence: String,
    val weekdays: Int?,
    val times: Int?,
    val measure: String,
    val target: Double?,
    val unit: String?,
)
