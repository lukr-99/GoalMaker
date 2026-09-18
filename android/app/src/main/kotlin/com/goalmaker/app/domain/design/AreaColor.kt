package com.goalmaker.app.domain.design

/** One of the 12 area colors, the same in every theme. */
data class AreaColor(
    val id: String,
    val swatch: Int,
    val light: ChipColors,
    val dark: ChipColors,
)
