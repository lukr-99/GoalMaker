package com.goalmaker.app.application.planning

/** "2 of 5 done": today's planned tasks that are done, of those open or done. */
data class DaySummary(
    val done: Int,
    val total: Int,
)
