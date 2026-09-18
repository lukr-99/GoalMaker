package com.goalmaker.app.data.sync

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

/** Background sync through WorkManager, which survives the app being closed (docs/sync.md). */
class WorkManagerSyncScheduler(private val context: Context) {
    private val online = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()

    /**
     * Every 15 minutes while signed in, whenever there is a network. KEEP, because this runs on every
     * start, including the start WorkManager makes to run this very work; replacing it would stop it.
     */
    fun keepSyncing() {
        val request = PeriodicWorkRequestBuilder<SyncWorker>(15, TimeUnit.MINUTES)
            .setConstraints(online)
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(PERIODIC, ExistingPeriodicWorkPolicy.KEEP, request)
    }

    /** Once, as soon as there is a network: changes are waiting and the last try was offline. */
    fun syncWhenOnline() {
        val request = OneTimeWorkRequestBuilder<SyncWorker>()
            .setConstraints(online)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(WHEN_ONLINE, ExistingWorkPolicy.KEEP, request)
    }

    fun stop() {
        val workManager = WorkManager.getInstance(context)
        workManager.cancelUniqueWork(PERIODIC)
        workManager.cancelUniqueWork(WHEN_ONLINE)
    }

    private companion object {
        const val PERIODIC = "goalmaker-sync"
        const val WHEN_ONLINE = "goalmaker-sync-when-online"
    }
}
