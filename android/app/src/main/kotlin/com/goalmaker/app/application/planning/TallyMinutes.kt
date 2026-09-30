package com.goalmaker.app.application.planning

/** One category's minutes in a stretch of days, as [TallyRules.byCategory] adds them up. */
data class TallyMinutes(val category: String, val minutes: Int)
