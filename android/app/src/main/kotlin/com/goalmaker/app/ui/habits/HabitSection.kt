package com.goalmaker.app.ui.habits

import com.goalmaker.app.application.planning.HabitGroup

/**
 * One group of the Habits screen (docs/habits.md): Every day, Weekly or Limits, with the [rows] shown
 * after Hide done and how many habits the group holds in [total].
 */
data class HabitSection(val group: HabitGroup, val rows: List<HabitRow>, val total: Int)
