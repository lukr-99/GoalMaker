package com.goalmaker.app.ui.tally

import java.time.LocalDate

/** One stacked bar: a day (or a week, from its Monday) with its minutes and each category's, most first. */
data class TallyBar(val day: LocalDate, val minutes: Int, val slices: List<TallySlice>)
