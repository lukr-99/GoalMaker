package com.goalmaker.app

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.goalmaker.app.ui.capture.CaptureViewModel
import com.goalmaker.app.ui.capture.QuickAddSheet
import com.goalmaker.app.ui.theme.GoalMakerTheme
import java.time.LocalDateTime

/**
 * The quick-add box the home screen widget opens: the composer over whatever the owner was doing,
 * without the app coming up. A line with no day lands in the Inbox; `--EXTRA_TODAY` starts it on
 * today. It closes as soon as the task is saved.
 */
class QuickAddActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val graph = (application as GoalMakerApplication).graph
        val forToday = intent?.getBooleanExtra(EXTRA_TODAY, false) == true
        setContent {
            val appearance by graph.settings.appearance.collectAsStateWithLifecycle()
            GoalMakerTheme(graph.design, appearance, graph.logo) {
                QuickAddSheet(
                    viewModel = viewModel {
                        CaptureViewModel(
                            tasks = graph.tasks,
                            areas = graph.areas,
                            tags = graph.tags,
                            projects = graph.projects,
                            auth = graph.auth,
                            dayStartHour = graph.settings.dayStartHour,
                            io = graph.io,
                            clock = LocalDateTime::now,
                        )
                    },
                    forToday = forToday,
                    onSaved = {
                        Toast.makeText(this, R.string.quick_add_saved, Toast.LENGTH_SHORT).show()
                        finish()
                    },
                    onCancel = { finish() },
                )
            }
        }
    }

    companion object {
        /** True when the widget's Today button opened it, so the line starts planned for today. */
        const val EXTRA_TODAY = "com.goalmaker.app.QUICK_ADD_TODAY"

        /** What the widget starts. */
        fun intent(context: android.content.Context, forToday: Boolean): Intent =
            Intent(context, QuickAddActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                .putExtra(EXTRA_TODAY, forToday)
    }
}
