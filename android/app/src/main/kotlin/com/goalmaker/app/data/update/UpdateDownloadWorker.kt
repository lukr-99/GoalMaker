package com.goalmaker.app.data.update

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.goalmaker.app.application.update.PrefetchResult

/**
 * Fetches the waiting update's APK ahead of Install, for WorkManager. [fetch] returns null when
 * there is nothing to fetch. A download that did not finish is tried again with backoff; one that
 * does not match the manifest is tried once more and then left to Install's own download.
 */
class UpdateDownloadWorker(
    context: Context,
    parameters: WorkerParameters,
    private val fetch: suspend () -> PrefetchResult?,
) : CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result = when (fetch()) {
        null, is PrefetchResult.Ready -> Result.success()
        is PrefetchResult.Failed -> Result.retry()
        PrefetchResult.Corrupted -> if (runAttemptCount < 1) Result.retry() else Result.failure()
    }
}
