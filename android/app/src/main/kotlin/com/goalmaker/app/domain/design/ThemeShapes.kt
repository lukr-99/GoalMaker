package com.goalmaker.app.domain.design

/** Corner radii in dp; [FULLY_ROUND] or more means a pill or a circle. */
data class ThemeShapes(
    val card: Int,
    val row: Int,
    val checkbox: Int,
    val button: Int,
) {
    companion object {
        const val FULLY_ROUND = 999
    }
}
