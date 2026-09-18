package com.goalmaker.app.domain.design

/** The reading font: its regular weight, the weight for emphasis, and its width. */
data class BodyStyle(
    val family: String,
    val weight: Int,
    val strongWeight: Int,
    val width: Float,
)
