package com.goalmaker.app.application.planning

import java.time.LocalDate

/** What the owner types for a calendar event (docs/calendar.md). */
data class EventDraft(
    val title: String,
    val startsOn: LocalDate,
    val endsOn: LocalDate = startsOn,
    val notes: String? = null,
    val areaId: String? = null,
)
