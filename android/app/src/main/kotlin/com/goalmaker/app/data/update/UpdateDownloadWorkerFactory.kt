package com.goalmaker.app.data.update

import android.content.Context
import androidx.work.ListenableWorker
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import com.goalmaker.app.application.update.PrefetchResult

/** Hands [UpdateDownloadWorker] its fetch function, so workers get their dependencies through constructors too. */
class UpdateDownloadWorkerFactory(private val fetch: suspend () -> PrefetchResult?) : WorkerFactory() {
    override fun createWorker(
        appContext: Context,
        workerClassName: String,
        workerParameters: WorkerParameters,
    ): ListenableWorker? =
        if (workerClassName == UpdateDownloadWorker::class.java.name) UpdateDownloadWorker(appContext, workerParameters, fetch) else null
}
