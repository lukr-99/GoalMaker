package com.goalmaker.app.ui.nav

import androidx.compose.animation.BoundsTransform
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.ContentScale
import androidx.navigation3.ui.LocalNavAnimatedContentScope
import com.goalmaker.app.ui.theme.AppTheme

/**
 * A task's surface as a shared element: the row in a list grows into the task's [details], and
 * leaving them drains the page back into the row, clipped to the row's [shape] (design spec, motion).
 * The details stay solid while they move and fade only as they land; the row fades out at once and
 * back in at the end. A back swipe drives it with the finger. Without the navigation around it, or
 * with reduce motion, the modifier does nothing.
 */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun Modifier.sharedTaskBounds(taskId: String, shape: Shape, details: Boolean): Modifier {
    val shared = LocalSharedTransitionScope.current ?: return this
    if (AppTheme.reduceMotion) return this
    val motion = AppTheme.motion
    val half = motion.emphasized / 2
    val visibility = LocalNavAnimatedContentScope.current
    return with(shared) {
        this@sharedTaskBounds.sharedBounds(
            sharedContentState = rememberSharedContentState(key = "task-$taskId"),
            animatedVisibilityScope = visibility,
            enter = if (details) fadeIn(tween(motion.quick)) else fadeIn(tween(half, delayMillis = half)),
            exit = if (details) fadeOut(tween(half, delayMillis = half)) else fadeOut(tween(motion.quick)),
            boundsTransform = BoundsTransform { _, _ -> tween(motion.emphasized, easing = NavTransitions.EMPHASIZED) },
            resizeMode = SharedTransitionScope.ResizeMode.scaleToBounds(ContentScale.FillWidth, Alignment.TopCenter),
            clipInOverlayDuringTransition = OverlayClip(shape),
        )
    }
}
