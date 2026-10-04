package com.goalmaker.app.ui.tally

/** One category in the Tally place's apps on this phone: its look, its minutes and its apps, most first. */
data class TallyAppGroup(
    val category: String,
    val name: String,
    val color: String,
    val emoji: String?,
    val minutes: Int,
    val apps: List<TallyAppRow>,
)
