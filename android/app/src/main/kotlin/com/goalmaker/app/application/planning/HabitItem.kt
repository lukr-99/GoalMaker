package com.goalmaker.app.application.planning

import java.time.LocalDate

/**
 * A habit as the screens and rules see it (docs/habits.md). [cadence] is daily, weekdays, per_week or
 * per_month; [weekdays] is the weekday bitmask (Monday 1 ... Sunday 64) and [times] the days a week or
 * month needs. [measure] is check, count or amount; [target] and [unit] belong to a count or an amount.
 */
data class HabitItem(
    val id: String,
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
    val archived: Boolean = false,
    val position: Double = 0.0,
    val deleted: Boolean = false,
)
