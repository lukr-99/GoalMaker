package com.goalmaker.app.application.planning

/** One category's seconds in an hour of the day, as [TallyBreakdown.hours] adds them up. */
data class TallySeconds(val category: String, val seconds: Int)
