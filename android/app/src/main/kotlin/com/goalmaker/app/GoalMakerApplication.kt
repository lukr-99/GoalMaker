package com.goalmaker.app

import android.app.Application
import androidx.work.Configuration
import com.goalmaker.app.composition.AppGraph
import com.goalmaker.app.data.diagnostics.CrashLog
import com.goalmaker.app.data.sync.SyncWorkerFactory

/**
 * Owns the composition root for the life of the process, and gives WorkManager a worker factory
 * wired to it (the default initializer is off in the manifest).
 */
class GoalMakerApplication : Application(), Configuration.Provider {
    lateinit var graph: AppGraph
        private set

    override fun onCreate() {
        super.onCreate()
        // Before the graph, so a crash while it is built is written down too.
        CrashLog.install(filesDir)
        graph = AppGraph(this)
    }

    // Looks the graph up when a worker runs, so WorkManager may initialize before onCreate finishes.
    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(SyncWorkerFactory { graph.syncInBackground() })
            .build()
}
