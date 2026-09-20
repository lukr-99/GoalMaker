package com.goalmaker.app.domain.sync

/** One column of a synced table. */
data class SyncedColumn(
    val name: String,
    val kind: ColumnKind,
    /**
     * The server has this column NOT NULL. A row carries every column, so a null in one of these is
     * refused on the push whatever default the column has (contracts/schemas/synced-tables.json).
     */
    val required: Boolean = false,
)
