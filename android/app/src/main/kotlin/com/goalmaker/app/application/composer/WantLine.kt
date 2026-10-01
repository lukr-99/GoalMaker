package com.goalmaker.app.application.composer

/** What the bar on Wants read from a line (docs/composer.md, "Adding on Wants, Habits and Goals"). */
data class WantLine(
    val title: String,
    val reason: String?,
    val price: Double?,
    val currency: String?,
    val waitDays: Int?,
)
