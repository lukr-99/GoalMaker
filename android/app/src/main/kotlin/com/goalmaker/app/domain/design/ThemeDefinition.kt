package com.goalmaker.app.domain.design

/** One of the switchable themes (ADR 0008), with its palettes for light, dark and pure black. */
data class ThemeDefinition(
    val id: String,
    val name: String,
    val summary: String,
    val typography: ThemeTypography,
    val shapes: ThemeShapes,
    val light: Palette,
    val dark: Palette,
    val black: Palette,
)
