package com.goalmaker.app.application.planning

import java.time.LocalDate

/**
 * A want as the Wants place shows it (docs/wants.md): what, why, what it costs, and the cooldown it
 * waits out before it is [decision]. [checkedPrice] is the last price Claude found, with a note.
 */
data class WantItem(
    val id: String,
    val title: String,
    val reason: String,
    val cooldownDays: Int,
    val addedOn: LocalDate,
    val coolsUntil: LocalDate,
    val link: String? = null,
    val price: Double? = null,
    val currency: String = WantCooldowns.DEFAULT.currency,
    val areaId: String? = null,
    val decision: String? = null,
    val decidedAt: String? = null,
    val decisionNote: String = "",
    val checkedPrice: Double? = null,
    val checkedAt: String? = null,
    val checkedNote: String = "",
    val madeBy: String = ProjectRules.OWNER,
    val deleted: Boolean = false,
    /** A want, or a need: something to buy, with no cooldown and maybe a day it is [needBy]. */
    val kind: String = WantRules.WANT,
    val needBy: LocalDate? = null,
)
