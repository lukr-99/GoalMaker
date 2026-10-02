package com.goalmaker.app.domain.settings

/**
 * What a Settings card does when it is hinted (contracts/vectors/settings.json, "hints"): how long
 * it rises, holds and fades in milliseconds, which layers show, how much of the left edge bar
 * grows and whether the title takes the highlight color. A rise or fade of 0 means a snap.
 */
data class SectionHint(
    val riseMillis: Int,
    val holdMillis: Int,
    val fadeMillis: Int,
    val tint: Boolean,
    val ring: Boolean,
    val glow: Boolean,
    val edge: Float,
    val title: Boolean,
)
