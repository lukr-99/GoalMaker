package com.goalmaker.app.ui.widget

/**
 * One task on the Today widget: what it says, the time beside it, and whether it is a top priority.
 * Only what is still open is listed, the same as the app's Today, so a tick takes the row away and
 * the header counts it.
 */
data class WidgetTask(
    val id: String,
    val title: String,
    val time: String = "",
    val topPriority: Boolean = false,
)
