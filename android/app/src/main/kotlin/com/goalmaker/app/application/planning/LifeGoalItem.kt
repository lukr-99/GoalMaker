package com.goalmaker.app.application.planning

import java.time.LocalDate

/**
 * A life goal as the Life goals place shows it (docs/life-goals.md): what the owner wants in their
 * life, [why] it matters, and the day they mean to have it [by]. [status] is open, achieved or
 * dropped, and [closedAt] says when it stopped being open.
 */
data class LifeGoalItem(
    val id: String,
    val title: String,
    val why: String,
    val by: LocalDate? = null,
    val areaId: String? = null,
    val status: String = LifeGoalRules.OPEN,
    val closedAt: String? = null,
    val position: Double = 0.0,
    val createdAt: String = "",
    val madeBy: String = ProjectRules.OWNER,
    val deleted: Boolean = false,
)
