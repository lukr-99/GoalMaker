package com.goalmaker.app.ui.activity

import com.goalmaker.app.application.activity.ActivityChange
import com.goalmaker.app.application.activity.ActivityEntry

/** One change in the Activity list; [undoable] when it is the row's latest change and not undone yet. */
data class ActivityRow(
    val entry: ActivityEntry,
    val change: ActivityChange,
    val undoable: Boolean,
)
