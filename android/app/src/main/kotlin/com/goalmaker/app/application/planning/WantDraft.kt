package com.goalmaker.app.application.planning

/**
 * What the owner types for a want (docs/wants.md). [pickedDays] overrides the cooldown the price
 * would give; it only counts when the want is added.
 */
data class WantDraft(
    val title: String,
    val reason: String,
    val link: String? = null,
    val price: Double? = null,
    val currency: String = WantCooldowns.DEFAULT.currency,
    val areaId: String? = null,
    val pickedDays: Int? = null,
)
