package com.goalmaker.app.domain.sync

/** What merging needs to know about one copy of a row: its server timestamp and whether it's a tombstone. */
data class RowVersion(
    val updatedAt: String,
    val deleted: Boolean,
)
