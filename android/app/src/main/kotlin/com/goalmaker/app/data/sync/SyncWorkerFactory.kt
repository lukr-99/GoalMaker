package com.goalmaker.app.data.sync

import android.content.Context
import androidx.work.ListenableWorker
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters

/** Hands [SyncWorker] its sync function, so workers get their dependencies through constructors too. */
class SyncWorkerFactory(private val sync: suspend () -> Boolean) : WorkerFactory() {
    override fun createWorker(
        appContext: Context,
        workerClassName: String,
        workerParameters: WorkerParameters,
    ): ListenableWorker? =
        if (workerClassName == SyncWorker::class.java.name) SyncWorker(appContext, workerParameters, sync) else null
}
