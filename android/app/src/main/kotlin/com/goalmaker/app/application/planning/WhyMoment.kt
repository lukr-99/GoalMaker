package com.goalmaker.app.application.planning

import java.time.LocalDate
import java.time.LocalDateTime

/** When a period's why reminder rings: the period it belongs to, by its first day, and the moment. */
data class WhyMoment(val periodStart: LocalDate, val at: LocalDateTime)
