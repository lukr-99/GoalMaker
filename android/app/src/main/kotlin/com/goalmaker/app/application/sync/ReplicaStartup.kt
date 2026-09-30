package com.goalmaker.app.application.sync

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Opens the replica once at start and says whether it worked (M6-06). A file that will not open
 * (a newer app's schema, a corrupt file) goes to [log] and becomes [ReplicaOpening.Failed], so the
 * app can say why it cannot go on instead of closing on the first screen that reads.
 */
class ReplicaStartup(
    private val replica: Replica,
    private val log: (Throwable) -> Unit,
) {
    private val mutableState = MutableStateFlow<ReplicaOpening>(ReplicaOpening.Opening)

    val state: StateFlow<ReplicaOpening> = mutableState.asStateFlow()

    /** Blocks on disk, so callers run it off the main thread. */
    fun open(): ReplicaOpening {
        val opened = try {
            replica.open()
            ReplicaOpening.Open
        } catch (error: Exception) {
            log(error)
            ReplicaOpening.Failed(newerApp = error is ReplicaFromNewerAppException)
        }
        mutableState.value = opened
        return opened
    }
}
