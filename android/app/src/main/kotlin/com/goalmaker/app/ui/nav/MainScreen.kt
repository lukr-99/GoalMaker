package com.goalmaker.app.ui.nav

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import com.goalmaker.app.ui.lists.ListTab
import com.goalmaker.app.ui.theme.AppTheme

/**
 * The five places the bottom bar reaches, as tabs of one screen: the bar stays put and only what is
 * above it changes, for the lists, Projects and the Calendar alike. What Back does between them lives
 * with the back stack, in [SignedInNavigation].
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MainScreen(
    current: MainDestination,
    listTab: ListTab,
    onSelect: (MainDestination) -> Unit,
    lists: @Composable (ListTab) -> Unit,
    projects: @Composable () -> Unit,
    calendar: @Composable () -> Unit,
) {
    val motion = AppTheme.motion
    val reduced = AppTheme.reduceMotion
    val transitions = remember(motion, reduced) { NavTransitions(motion, reduced) }
    Scaffold(
        // Each place's own top bar reaches under the status bar; this screen only holds the bottom bar.
        contentWindowInsets = WindowInsets(0),
        bottomBar = {
            // The keyboard takes the bar's place while something is being typed.
            if (!WindowInsets.isImeVisible) MainNavigationBar(current, onSelect)
        },
    ) { padding ->
        // The three lists are one screen that switches its own tabs, so here they count as one place.
        AnimatedContent(
            targetState = current.takeIf { it.tab() == null },
            transitionSpec = { transitions.switch() },
            label = "main place",
            modifier = Modifier.padding(padding).consumeWindowInsets(padding),
        ) { place ->
            when (place) {
                MainDestination.PROJECTS -> projects()
                MainDestination.CALENDAR -> calendar()
                else -> lists(listTab)
            }
        }
    }
}
