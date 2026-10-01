package com.goalmaker.app.ui.habits

/**
 * The Habits screen's summary card: how many of the habits that ask something of today are [done] of
 * [total] (limits, skips and pauses ask nothing), how far the day has got in [share] (a habit still to do
 * counts its ring), and the habit with the longest streak, [best], or null before any streak.
 */
data class HabitSummary(val done: Int = 0, val total: Int = 0, val share: Double = 0.0, val best: HabitRow? = null)
