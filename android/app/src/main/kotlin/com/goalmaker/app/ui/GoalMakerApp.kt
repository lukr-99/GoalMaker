package com.goalmaker.app.ui

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.goalmaker.app.application.auth.AuthSession
import com.goalmaker.app.composition.AppGraph
import com.goalmaker.app.ui.nav.SignedInNavigation
import com.goalmaker.app.ui.signin.SignInScreen
import com.goalmaker.app.ui.signin.SignInViewModel
import com.goalmaker.app.ui.theme.GoalMakerTheme

/** Root composable: the theme, then sign-in or the signed-in app depending on the session. */
@Composable
fun GoalMakerApp(graph: AppGraph) {
    val themeMode by graph.settings.themeMode.collectAsStateWithLifecycle()
    val session by graph.auth.session.collectAsStateWithLifecycle()
    GoalMakerTheme(themeMode) {
        Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxSize()) {
            Crossfade(targetState = session, label = "session") { current ->
                when (current) {
                    AuthSession.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        LoadingIndicator(Modifier.size(64.dp))
                    }
                    AuthSession.SignedOut -> SignInScreen(
                        viewModel = viewModel { SignInViewModel(graph.auth) },
                        backendLabel = graph.appInfo.backend.url.takeIf { graph.appInfo.isDevBuild },
                    )
                    is AuthSession.SignedIn -> SignedInNavigation(graph = graph)
                }
            }
        }
    }
}
