package com.goalmaker.app.domain.sync

/** The outcome of merging one pulled row (contracts/vectors/sync-merge.json). */
data class MergeDecision(
    val takeRemote: Boolean,
    val dropPending: Boolean,
)
