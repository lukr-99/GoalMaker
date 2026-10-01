package com.goalmaker.app

import android.app.Application
import androidx.work.Configuration
import androidx.work.DelegatingWorkerFactory
import com.goalmaker.app.composition.AppGraph
import com.goalmaker.app.data.diagnostics.CrashLog
import com.goalmaker.app.data.sync.SyncWorkerFactory
import com.goalmaker.app.data.update.UpdateDownloadWorkerFactory

/**
 * Owns the composition root for the life of the process, and gives WorkManager worker factories
 * wired to it (the default initializer is off in the manifest): background sync and the update
 * download.
 */
class GoalMakerApplication : Application(), Configuration.Provider {
    lateinit var graph: AppGraph
        private set

    /**
     * Why the app could not start, usually its data file. The screen says so rather than leaving
     * Android to close a window that never appeared (M6-06).
     */
    var startupFailure: Throwable? = null
        private set

    override fun onCreate() {
        super.onCreate()
        // Before the graph, so a crash while it is built is written down too.
        CrashLog.install(filesDir)
        try {
            graph = AppGraph(this)
        } catch (error: Throwable) {
            startupFailure = error
            CrashLog.write(filesDir, error)
        }
    }

    // Looks the graph up when a worker runs, so WorkManager may initialize before onCreate finishes.
    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(
                DelegatingWorkerFactory().apply {
                    addFactory(SyncWorkerFactory { startupFailure == null && graph.syncInBackground() })
                    addFactory(UpdateDownloadWorkerFactory { if (startupFailure == null) graph.prefetchUpdate() else null })
                },
            )
            .build()
}
