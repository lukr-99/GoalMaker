package com.goalmaker.app.ui.today

import com.goalmaker.app.application.planning.AreaItem
import com.goalmaker.app.application.planning.TaskItem
import com.goalmaker.app.application.sync.SyncStatus

/**
 * Everything Today shows. [loaded] is false until the replica has been read once; [refreshing] is
 * true only while a sync the user pulled for runs. [areas] and [tagNames] tell the composer's
 * preview which names already exist.
 */
data class TodayUiState(
    val tasks: List<TaskItem>,
    val loaded: Boolean,
    val sync: SyncStatus,
    val refreshing: Boolean,
    val areas: List<AreaItem> = emptyList(),
    val tagNames: List<String> = emptyList(),
)
