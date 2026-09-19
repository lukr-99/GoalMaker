package com.goalmaker.app.application.planning

import java.time.LocalDate

/**
 * A habit as the owner typed it, before [HabitList] cleans it: the fields that don't fit the cadence
 * or the measure are dropped there (docs/habits.md).
 */
data class HabitDraft(
    val name: String,
    val startsOn: LocalDate,
    val cadence: String = HabitRules.DAILY,
    val weekdays: Int? = null,
    val times: Int? = null,
    val measure: String = HabitRules.CHECK,
    val target: Double? = null,
    val unit: String? = null,
    val emoji: String? = null,
    val goalId: String? = null,
)
