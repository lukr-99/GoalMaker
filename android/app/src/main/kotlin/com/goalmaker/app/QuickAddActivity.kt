package com.goalmaker.app

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.fragment.app.FragmentActivity
import com.goalmaker.app.ui.ReplicaGate
import com.goalmaker.app.ui.capture.CaptureViewModel
import com.goalmaker.app.ui.capture.QuickAddSheet
import com.goalmaker.app.ui.chat.ChatViewModel
import com.goalmaker.app.domain.settings.ComposerMode
import com.goalmaker.app.ui.lock.LockedWindow
import com.goalmaker.app.ui.theme.GoalMakerTheme
import java.time.LocalDateTime

/**
 * The quick-add box the home screen widget opens: the composer over whatever the owner was doing,
 * without the app coming up. A line with no day lands in the Inbox; [EXTRA_TODAY] starts it on
 * today. It closes as soon as the task is saved. [EXTRA_CHAT] (the widget's sparkle) opens it in
 * chat mode instead, which stays open for the answer; the thread goes when the box closes.
 *
 * A FragmentActivity because the app lock can stand in front of this window too, and the unlock
 * prompt is a fragment (docs/sign-in.md).
 */
class QuickAddActivity : FragmentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val graph = (application as GoalMakerApplication).graph
        val forToday = intent?.getBooleanExtra(EXTRA_TODAY, false) == true
        // Each of the widget's buttons is a pick of the composer's mode, the same pick as its switch.
        if (savedInstanceState == null) {
            val chat = intent?.getBooleanExtra(EXTRA_CHAT, false) == true
            graph.settings.setComposerMode(if (chat) ComposerMode.CHAT else ComposerMode.QUICK_ADD)
        }
        setContent {
            val appearance by graph.settings.appearance.collectAsStateWithLifecycle()
            GoalMakerTheme(graph.design, appearance, graph.logo) {
                ReplicaGate(graph) {
                LockedWindow(graph = graph, onGiveUp = ::finish) {
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
                    chat = viewModel {
                        ChatViewModel(
                            assistant = graph.assistant,
                            settings = graph.settings,
                            session = graph.chatSession,
                            syncStatus = graph.sync.status,
                            syncNow = { graph.sync.syncNow() },
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
        }
    }

    companion object {
        /** True when the widget's Today button opened it, so the line starts planned for today. */
        const val EXTRA_TODAY = "com.goalmaker.app.QUICK_ADD_TODAY"

        /** True when the widget's sparkle opened it, so the composer starts in chat mode. */
        const val EXTRA_CHAT = "com.goalmaker.app.QUICK_ADD_CHAT"

        /** What the widget starts. */
        fun intent(context: android.content.Context, forToday: Boolean, chat: Boolean = false): Intent =
            Intent(context, QuickAddActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                .putExtra(EXTRA_TODAY, forToday)
                .putExtra(EXTRA_CHAT, chat)
    }
}
