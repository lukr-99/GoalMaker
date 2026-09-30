package com.goalmaker.app.application.planning

import java.time.LocalDate

/** A device's minutes in one category and project on one planning day, as [TallyRules.dayTotals] adds them up. */
data class TallyTotal(val day: LocalDate, val category: String, val project: String?, val minutes: Int)
