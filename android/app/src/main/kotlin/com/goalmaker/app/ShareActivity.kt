package com.goalmaker.app

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.runtime.getValue
import com.goalmaker.app.domain.share.SharedCapture
import com.goalmaker.app.ui.capture.ShareScreen
import com.goalmaker.app.ui.capture.CaptureViewModel
import com.goalmaker.app.ui.lock.LockedWindow
import com.goalmaker.app.ui.theme.GoalMakerTheme
import java.time.LocalDateTime

/**
 * Where a share from another app lands (spec, story 10): the shared text and link become a task,
 * through the same composer line the app itself uses. It never opens the main window, so sharing
 * takes one tap and leaves the owner where they were.
 *
 * A FragmentActivity because the app lock can stand in front of this window too, and the unlock
 * prompt is a fragment (docs/sign-in.md).
 */
class ShareActivity : FragmentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val graph = (application as GoalMakerApplication).graph
        val capture = SharedCapture.of(
            subject = intent?.getStringExtra(Intent.EXTRA_SUBJECT),
            text = intent?.getStringExtra(Intent.EXTRA_TEXT),
        )
        setContent {
            val appearance by graph.settings.appearance.collectAsStateWithLifecycle()
            GoalMakerTheme(graph.design, appearance, graph.logo) {
                Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxSize()) {
                    LockedWindow(graph = graph, onGiveUp = ::finish) {
                    ShareScreen(
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
                        capture = capture,
                        onSaved = {
                            Toast.makeText(this, R.string.share_saved, Toast.LENGTH_SHORT).show()
                            finish()
                        },
                        onCancel = { finish() },
                        onOpenApp = {
                            startActivity(Intent(this, MainActivity::class.java))
                            finish()
                        },
                    )
                    }
                }
            }
        }
    }
}
