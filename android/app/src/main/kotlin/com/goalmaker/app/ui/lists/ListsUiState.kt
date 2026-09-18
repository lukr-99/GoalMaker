package com.goalmaker.app.ui.lists

import com.goalmaker.app.application.planning.AreaItem
import com.goalmaker.app.application.planning.PlanningLists
import com.goalmaker.app.application.sync.SyncStatus

/**
 * Everything the lists show. [lists] is null until the replica has been read once; [refreshing] is
 * true only while a sync the user pulled for runs. [areas] and [tagNames] feed the rows and the
 * composer's preview.
 */
data class ListsUiState(
    val lists: PlanningLists?,
    val sync: SyncStatus,
    val refreshing: Boolean,
    val areas: List<AreaItem> = emptyList(),
    val tagNames: List<String> = emptyList(),
)
