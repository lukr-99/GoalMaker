package com.goalmaker.app.data.update

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.goalmaker.app.GoalMakerApplication

/** Later on the update notification: the version stays quiet for three days (docs/setup/signing-and-releases.md). */
class UpdateActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != UpdateIntents.ACTION_LATER) return
        val app = context.applicationContext as? GoalMakerApplication ?: return
        if (app.startupFailure != null) return
        val version = intent.getStringExtra(UpdateIntents.EXTRA_VERSION) ?: return
        app.graph.updateAlerts.later(version)
    }
}
