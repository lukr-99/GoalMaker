package com.goalmaker.app.ui

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.activity.compose.LocalActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.goalmaker.app.R
import com.goalmaker.app.application.auth.AuthSession
import com.goalmaker.app.composition.AppGraph
import com.goalmaker.app.ui.lock.LockScreen
import com.goalmaker.app.ui.nav.SignedInNavigation
import com.goalmaker.app.ui.signin.SignInScreen
import com.goalmaker.app.ui.signin.SignInViewModel
import com.goalmaker.app.ui.components.LaunchIntro
import com.goalmaker.app.ui.theme.GoalMakerTheme
import kotlinx.coroutines.launch

/**
 * Root composable: the theme, then sign-in or the signed-in app depending on the session, with
 * the optional lock over the top of it (docs/sign-in.md).
 */
@Composable
fun GoalMakerApp(graph: AppGraph) {
    val appearance by graph.settings.appearance.collectAsStateWithLifecycle()
    val session by graph.auth.session.collectAsStateWithLifecycle()
    val locked by graph.appLock.locked.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    GoalMakerTheme(graph.design, appearance, graph.logo) {
        LaunchIntro {
            Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxSize()) {
                Crossfade(targetState = session, label = "session") { current ->
                    when (current) {
                        AuthSession.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            LoadingIndicator(Modifier.size(64.dp))
                        }
                        AuthSession.SignedOut -> SignInScreen(
                            viewModel = viewModel { SignInViewModel(graph.auth, graph.devCode, graph.appLock::unlocked) },
                            backendLabel = graph.appInfo.backend.url.takeIf { graph.appInfo.isDevBuild },
                        )
                        // The lock sits over the app rather than in place of it, so a glance
                        // at another app does not cost the owner where they were (docs/sign-in.md).
                        is AuthSession.SignedIn -> Box(Modifier.fillMaxSize()) {
                            SignedInNavigation(graph = graph)
                            if (locked) {
                                val activity = LocalActivity.current
                                LockScreen(
                                    unlock = graph.deviceUnlock,
                                    onUnlocked = graph.appLock::unlocked,
                                    onGiveUp = { scope.launch { graph.auth.signOut() } },
                                    giveUpLabel = stringResource(R.string.lock_use_code),
                                    onBack = { activity?.moveTaskToBack(true) },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
