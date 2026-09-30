package com.goalmaker.app.application.planning

import java.time.LocalDate

/** One week of the stats block: its Monday, its minutes, and each category's, most first. */
data class TallyWeek(val start: LocalDate, val minutes: Int, val categories: List<TallyMinutes>)
