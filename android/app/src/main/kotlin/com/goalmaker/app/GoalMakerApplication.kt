package com.goalmaker.app

import android.app.Application
import com.goalmaker.app.composition.AppGraph

/** Owns the composition root for the life of the process. */
class GoalMakerApplication : Application() {
    lateinit var graph: AppGraph
        private set

    override fun onCreate() {
        super.onCreate()
        graph = AppGraph(this)
    }
}
