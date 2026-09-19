package com.goalmaker.app.application.planning

import java.time.LocalDate

/** Days a habit rests, [from] to [until] (null while the pause lasts): they neither break its streak nor count. */
data class HabitPause(
    val id: String,
    val habitId: String,
    val from: LocalDate,
    val until: LocalDate? = null,
    val deleted: Boolean = false,
)
