package com.goalmaker.app.domain.design

/** Animation durations in milliseconds (docs/design/spec.md, "Motion and feedback"). */
data class MotionTokens(
    val quick: Int,
    val standard: Int,
    val emphasized: Int,
)
