package com.goalmaker.app.application.planning

import java.time.LocalDate

/** What the owner types for a life goal (docs/life-goals.md). */
data class LifeGoalDraft(
    val title: String,
    val why: String,
    val by: LocalDate? = null,
    val areaId: String? = null,
)
