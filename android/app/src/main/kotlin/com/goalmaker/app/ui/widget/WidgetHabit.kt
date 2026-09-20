package com.goalmaker.app.ui.widget

/**
 * One habit on the Habits widget: what it is called, how far today has got ([ring], 0 to 1), and
 * whether one tap can check it in. A habit measured by an amount asks for its value, so it opens the
 * app instead.
 */
data class WidgetHabit(
    val id: String,
    val name: String,
    val emoji: String,
    val ring: Double,
    val done: Boolean,
    val count: String = "",
    val tappable: Boolean = true,
)
