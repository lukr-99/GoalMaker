package com.goalmaker.app.application.planning

import java.time.LocalDate

/** A why reminder to show (docs/life-goals.md): the period it belongs to and the life goal it names. */
data class WhyDue(val periodStart: LocalDate, val lifeGoalId: String)
