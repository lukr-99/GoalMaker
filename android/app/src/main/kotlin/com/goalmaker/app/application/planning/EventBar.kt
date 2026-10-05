package com.goalmaker.app.application.planning

/**
 * The piece of an [event]'s bar one week row of the grid draws (docs/calendar.md): from column [from]
 * to column [to] (0 is Monday), in [lane]. [before] and [after] say whether the event goes on before
 * the row or after it, so the bar's ends can show it.
 */
data class EventBar(
    val event: EventItem,
    val from: Int,
    val to: Int,
    val lane: Int,
    val before: Boolean,
    val after: Boolean,
)
