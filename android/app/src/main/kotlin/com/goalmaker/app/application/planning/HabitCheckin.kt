package com.goalmaker.app.application.planning

import java.time.LocalDate

/**
 * A day's one check-in on a habit: the day's [value], or its period [skipped], or its period [failed]
 * (the owner said so: missed at once, today included) (docs/habits.md).
 */
data class HabitCheckin(
    val id: String,
    val habitId: String,
    val day: LocalDate,
    val value: Double,
    val skipped: Boolean = false,
    val failed: Boolean = false,
    val deleted: Boolean = false,
)
