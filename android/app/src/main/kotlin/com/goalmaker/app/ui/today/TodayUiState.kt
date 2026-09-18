package com.goalmaker.app.ui.today

import com.goalmaker.app.application.planning.TaskItem
import com.goalmaker.app.application.sync.SyncStatus

/**
 * Everything Today shows. [loaded] is false until the replica has been read once; [refreshing] is
 * true only while a sync the user pulled for runs.
 */
data class TodayUiState(
    val tasks: List<TaskItem>,
    val loaded: Boolean,
    val sync: SyncStatus,
    val refreshing: Boolean,
)
