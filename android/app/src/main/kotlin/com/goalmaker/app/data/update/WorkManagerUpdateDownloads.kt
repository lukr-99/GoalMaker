package com.goalmaker.app.data.update

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequest
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.goalmaker.app.application.update.UpdateDownloads
import java.util.concurrent.TimeUnit

/**
 * The background download of a found update through WorkManager: one-time work that waits for an
 * unmetered network and a battery that is not low, so it never costs mobile data or a flat phone.
 */
class WorkManagerUpdateDownloads(private val context: Context) : UpdateDownloads {

    // KEEP: a check while the download waits or runs leaves it be.
    override fun schedule() {
        WorkManager.getInstance(context).enqueueUniqueWork(NAME, ExistingWorkPolicy.KEEP, request())
    }

    override fun cancel() {
        WorkManager.getInstance(context).cancelUniqueWork(NAME)
    }

    companion object {
        private const val NAME = "goalmaker-update-download"

        /** The work request: unmetered network, battery not low, backoff from a minute. */
        fun request(): OneTimeWorkRequest = OneTimeWorkRequestBuilder<UpdateDownloadWorker>()
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.UNMETERED)
                    .setRequiresBatteryNotLow(true)
                    .build(),
            )
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 1, TimeUnit.MINUTES)
            .build()
    }
}
