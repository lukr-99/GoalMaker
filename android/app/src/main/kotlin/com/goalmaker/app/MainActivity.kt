package com.goalmaker.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import com.goalmaker.app.data.planning.ReminderAlarm
import com.goalmaker.app.ui.GoalMakerApp
import com.goalmaker.app.ui.StartupFailureScreen

class MainActivity : ComponentActivity() {
    // Reminders are useless without notifications; from API 33 the owner has to allow them.
    private val askForNotifications = registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val app = application as GoalMakerApplication
        if (app.startupFailure != null) {
            setContent { StartupFailureScreen() }
            return
        }

        val graph = app.graph
        requestNotifications()
        openedFromReminder(intent)
        setContent { GoalMakerApp(graph) }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        openedFromReminder(intent)
    }

    private fun openedFromReminder(intent: Intent?) {
        if (intent == null) return
        val app = application as GoalMakerApplication
        // Nothing to open when the app could not start (M6-06).
        if (app.startupFailure != null) return
        val graph = app.graph
        val reviewKind = intent.getStringExtra(ReminderAlarm.EXTRA_REVIEW_KIND)
        val reviewPeriod = intent.getStringExtra(ReminderAlarm.EXTRA_REVIEW_PERIOD)
        if (reviewKind != null && reviewPeriod != null) {
            runCatching { java.time.LocalDate.parse(reviewPeriod) }.getOrNull()?.let { start ->
                graph.openedForReview(reviewKind, start)
            }
            intent.removeExtra(ReminderAlarm.EXTRA_REVIEW_KIND)
            intent.removeExtra(ReminderAlarm.EXTRA_REVIEW_PERIOD)
        }
        if (intent.getBooleanExtra(ReminderAlarm.EXTRA_OPEN_PLAN, false)) {
            graph.openedForPlan()
            intent.removeExtra(ReminderAlarm.EXTRA_OPEN_PLAN)
        }
        val reminderId = intent.getStringExtra(ReminderAlarm.EXTRA_REMINDER_ID) ?: return
        graph.openedFromReminder(reminderId)
        intent.removeExtra(ReminderAlarm.EXTRA_REMINDER_ID)
    }

    private fun requestNotifications() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        val granted = ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
        if (!granted) askForNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
    }
}
