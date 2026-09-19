package com.goalmaker.app.ui.nav

import androidx.compose.animation.ContentTransform
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.navigation3.ui.NavDisplay
import com.goalmaker.app.domain.design.MotionTokens

/**
 * How the signed-in screens move (design spec, "Motion and feedback"): a new screen slides in a
 * little and fades up, Back slides it away, and a back swipe shrinks it with the finger. A task's
 * details grow out of its row and drain back into it ([sharedTaskBounds]) while the list behind
 * recedes and comes back. With reduce motion, every change is a short fade.
 */
class NavTransitions(private val motion: MotionTokens, private val reduced: Boolean) {
    fun forward(): ContentTransform = if (reduced) {
        fade()
    } else {
        (fadeIn(tween(motion.standard, delayMillis = motion.quick / 2)) + slideInHorizontally(tween(motion.emphasized, easing = EMPHASIZED)) { it / 8 })
            .togetherWith(fadeOut(tween(motion.quick)) + slideOutHorizontally(tween(motion.emphasized, easing = EMPHASIZED)) { -it / 16 })
    }

    fun back(): ContentTransform = if (reduced) {
        fade()
    } else {
        (fadeIn(tween(motion.standard)) + slideInHorizontally(tween(motion.emphasized, easing = EMPHASIZED)) { -it / 16 })
            .togetherWith(fadeOut(tween(motion.standard)) + slideOutHorizontally(tween(motion.emphasized, easing = EMPHASIZED)) { it / 8 })
    }

    /** A back swipe in progress: the screen shrinks and fades as the finger moves. */
    fun backSwipe(): ContentTransform = if (reduced) {
        fade()
    } else {
        fadeIn(tween(motion.standard)).togetherWith(fadeOut(tween(motion.standard)) + scaleOut(tween(motion.standard), targetScale = 0.9f))
    }

    /** The task details' entry: the shared bounds move the details, the list only recedes and returns. */
    fun task(): Map<String, Any> = if (reduced) {
        emptyMap()
    } else {
        NavDisplay.transitionSpec { recede() } + NavDisplay.popTransitionSpec { returnTo() } + NavDisplay.predictivePopTransitionSpec { returnTo() }
    }

    private fun fade() = fadeIn(tween(motion.quick)).togetherWith(fadeOut(tween(motion.quick)))

    private fun recede() = EnterTransition.None.togetherWith(fadeOut(tween(motion.emphasized), targetAlpha = BEHIND))

    private fun returnTo() = fadeIn(tween(motion.emphasized), initialAlpha = BEHIND).togetherWith(ExitTransition.None)

    companion object {
        /** Material's emphasized easing: quick to leave, long to settle. */
        val EMPHASIZED = CubicBezierEasing(0.2f, 0f, 0f, 1f)

        // How much of the list shows behind a task's details while they move: dimmed a little, and
        // kept for the whole move, which also keeps it composed under the details.
        private const val BEHIND = 0.85f
    }
}
