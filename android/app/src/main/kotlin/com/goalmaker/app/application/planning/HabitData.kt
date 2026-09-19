package com.goalmaker.app.application.planning

/** Every habit that isn't deleted, with the check-ins and pauses of all of them, as [HabitList] reads them. */
data class HabitData(
    val habits: List<HabitItem> = emptyList(),
    val checkins: List<HabitCheckin> = emptyList(),
    val pauses: List<HabitPause> = emptyList(),
) {
    fun checkinsOf(habitId: String): List<HabitCheckin> = checkins.filter { it.habitId == habitId }

    fun pausesOf(habitId: String): List<HabitPause> = pauses.filter { it.habitId == habitId }
}
