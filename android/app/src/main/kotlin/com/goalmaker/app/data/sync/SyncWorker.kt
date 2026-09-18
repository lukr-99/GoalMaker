package com.goalmaker.app.data.sync

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters

/**
 * A background sync run for WorkManager (docs/sync.md: every 15 minutes, and when the network comes
 * back with changes waiting). [sync] returns false when the server couldn't be reached, which asks
 * WorkManager to retry with backoff. Created by [SyncWorkerFactory].
 */
class SyncWorker(
    context: Context,
    parameters: WorkerParameters,
    private val sync: suspend () -> Boolean,
) : CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result = if (sync()) Result.success() else Result.retry()
}
