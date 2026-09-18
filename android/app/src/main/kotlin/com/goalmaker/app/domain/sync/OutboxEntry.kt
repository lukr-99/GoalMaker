package com.goalmaker.app.domain.sync

/** A local change waiting to be pushed: the full row as JSON, in queue order. */
data class OutboxEntry(
    val seq: Long,
    val entity: String,
    val rowId: String,
    val payload: String,
    val attempts: Int,
    val lastError: String?,
)
