package com.goalmaker.app.domain.sync

/** One column of a synced table. */
data class SyncedColumn(
    val name: String,
    val kind: ColumnKind,
)
