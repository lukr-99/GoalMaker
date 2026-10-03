package com.goalmaker.app.application.planning

import java.time.LocalDate

/** A habit whose reminder a look found due on planning [day] (docs/reminders.md). */
data class DueHabit(val habit: HabitItem, val day: LocalDate)
