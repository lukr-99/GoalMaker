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
import com.goalmaker.app.ui.theme.AppTheme

/**
 * Every place, as tabs of one screen under one bottom bar (ADR 0014): the pinned places and the
 * Places hub, and a place opened from the hub, which keeps the bar with Places selected. What Back
 * does between them lives with the back stack, in [SignedInNavigation].
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MainScreen(
    current: String,
    pins: List<String>,
    placesCount: Int,
    onSelect: (String) -> Unit,
    content: @Composable (screen: String) -> Unit,
) {
    val motion = AppTheme.motion
    val reduced = AppTheme.reduceMotion
    val transitions = remember(motion, reduced) { NavTransitions(motion, reduced) }
    Scaffold(
        // Each place's own top bar reaches under the status bar; this screen only holds the bottom bar.
        contentWindowInsets = WindowInsets(0),
        bottomBar = {
            // The keyboard takes the bar's place while something is being typed.
            if (!WindowInsets.isImeVisible) MainNavigationBar(pins, current, placesCount, onSelect)
        },
    ) { padding ->
        // The three lists are one screen that switches its own tabs, so here they count as one screen.
        AnimatedContent(
            targetState = if (PlaceLook.tab(current) != null) PlaceLook.LISTS else current,
            transitionSpec = { transitions.switch() },
            label = "main place",
            modifier = Modifier.padding(padding).consumeWindowInsets(padding),
        ) { screen -> content(screen) }
    }
}
