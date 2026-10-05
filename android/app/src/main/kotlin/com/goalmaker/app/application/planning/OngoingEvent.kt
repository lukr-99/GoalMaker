package com.goalmaker.app.application.planning

/** An event the planning day falls inside, on its day [dayOf] of [days] (docs/calendar.md). */
data class OngoingEvent(val event: EventItem, val dayOf: Int, val days: Int)
