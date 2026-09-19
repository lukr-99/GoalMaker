package com.goalmaker.app.ui.nav

import androidx.compose.animation.SharedTransitionScope
import androidx.compose.runtime.staticCompositionLocalOf

/**
 * The scope the signed-in screens share elements in: a task row grows into the task's details and
 * shrinks back into it. Null outside the navigation (previews, tests), where nothing is shared.
 */
val LocalSharedTransitionScope = staticCompositionLocalOf<SharedTransitionScope?> { null }
