package com.goalmaker.app.domain.design

/**
 * How a Settings card lights up in one theme and mode (contracts/design/themes.json, "highlight"):
 * the theme's brightest accent [spot], mixed into the card at [tint]; a 2 dp inner ring in [ring]
 * at [ringAlpha]; a 4 dp soft glow of the spot at [glowAlpha]; the 3 dp left edge bar in [edge];
 * and the title in [title] at the peak. Colors are opaque ARGB integers.
 */
data class HighlightTokens(
    val spot: Int,
    val tint: Float,
    val ring: Int,
    val ringAlpha: Float,
    val glowAlpha: Float,
    val edge: Int,
    val title: Int,
)
