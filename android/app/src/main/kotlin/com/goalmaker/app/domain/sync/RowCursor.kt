package com.goalmaker.app.domain.sync

/** Where the next pull page starts: after this (updated_at, id) pair (docs/sync.md, keyset paging). */
data class RowCursor(
    val updatedAt: String,
    val id: String,
)
