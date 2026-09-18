package com.goalmaker.app.domain.design

/**
 * One theme's colors for one mode, by role (contracts/design/themes.json, "roles"). Colors are ARGB
 * integers so the domain stays free of UI types.
 */
data class Palette(
    val background: Int,
    val surface: Int,
    val surfaceVariant: Int,
    val text: Int,
    val textMuted: Int,
    val outline: Int,
    val primary: Int,
    val onPrimary: Int,
    val accent: Int,
    val onAccent: Int,
    val hero: Int,
    val onHero: Int,
    val heroAccent: Int,
    val danger: Int,
)
