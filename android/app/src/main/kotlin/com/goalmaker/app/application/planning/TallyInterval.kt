package com.goalmaker.app.application.planning

import java.time.LocalDateTime

/** A stretch of foreground time in local time, already sorted into a category and project. */
data class TallyInterval(val start: LocalDateTime, val end: LocalDateTime, val category: String, val project: String?)
