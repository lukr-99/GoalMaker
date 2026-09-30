package com.goalmaker.app.application.planning

/**
 * One Tally rule (docs/tally.md): time whose app, window title or editor folder ([match]) fits
 * [pattern], on [platform], goes to [category]. The owner's own rules have an [id] and may name a
 * [project]; the shipped defaults (contracts/content/tally-rules.json) have neither.
 */
data class TallyRule(
    val match: String,
    val pattern: String,
    val platform: String,
    val category: String,
    val project: String? = null,
    val id: String? = null,
)
