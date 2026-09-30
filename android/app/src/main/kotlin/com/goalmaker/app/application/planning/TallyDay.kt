package com.goalmaker.app.application.planning

import java.time.LocalDate

/**
 * One synced Tally row (docs/tally.md): the [minutes] one [device] spent in a category and project on
 * a planning day. [deviceKind] is `phone` or `pc`; a phone's rows never name a project.
 */
data class TallyDay(
    val id: String,
    val day: LocalDate,
    val device: String,
    val deviceKind: String,
    val category: String,
    val project: String?,
    val minutes: Int,
)
