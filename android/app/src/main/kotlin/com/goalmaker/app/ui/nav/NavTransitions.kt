package com.goalmaker.app.ui.nav

import androidx.compose.animation.ContentTransform
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.navigation3.scene.Scene
import androidx.navigation3.ui.NavDisplay
import com.goalmaker.app.domain.design.MotionTokens

/**
 * How the signed-in screens move (design spec, "Motion and feedback"): going deeper slides the new
 * screen in a little and fades it up, Back slides it away, and a back swipe shrinks it with the
 * finger. The places the bottom bar reaches are side by side rather than one inside the other, so
 * moving between them fades through ([switch]) instead of sliding, whether that is a list tab or
 * Projects and the Calendar. A task's details grow out of its row and drain back into it
 * ([sharedTaskBounds]) while the list behind recedes and comes back. With reduce motion, every
 * change is a short fade.
 */
class NavTransitions(private val motion: MotionTokens, private val reduced: Boolean) {
    fun forward(): ContentTransform = if (reduced) {
        fade()
    } else {
        // The two fades overlap for the whole move, so the screens cross rather than leaving a gap.
        (fadeIn(tween(motion.standard, easing = DECELERATE)) + slideInHorizontally(tween(motion.emphasized, easing = EMPHASIZED)) { it / 10 })
            .togetherWith(fadeOut(tween(motion.standard, easing = ACCELERATE)) + slideOutHorizontally(tween(motion.emphasized, easing = EMPHASIZED)) { -it / 20 })
    }

    fun back(): ContentTransform = if (reduced) {
        fade()
    } else {
        (fadeIn(tween(motion.standard, easing = DECELERATE)) + slideInHorizontally(tween(motion.emphasized, easing = EMPHASIZED)) { -it / 20 })
            .togetherWith(fadeOut(tween(motion.standard, easing = ACCELERATE)) + slideOutHorizontally(tween(motion.emphasized, easing = EMPHASIZED)) { it / 10 })
    }

    /**
     * A move between the places the bottom bar reaches: nothing slides, since neither is above the
     * other. The one leaving fades out as the one arriving fades and grows the last of the way in.
     */
    fun switch(): ContentTransform = if (reduced) {
        fade()
    } else {
        (fadeIn(tween(motion.standard, delayMillis = motion.quick / 2, easing = DECELERATE)) +
            scaleIn(tween(motion.emphasized, easing = EMPHASIZED), initialScale = SETTLE))
            .togetherWith(fadeOut(tween(motion.quick, easing = ACCELERATE)))
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

        // Its two halves, for whatever only arrives or only leaves.
        private val DECELERATE = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f)
        private val ACCELERATE = CubicBezierEasing(0.3f, 0f, 0.8f, 0.15f)

        // How much of the list shows behind a task's details while they move: dimmed a little, and
        // kept for the whole move, which also keeps it composed under the details.
        private const val BEHIND = 0.85f

        // How small the arriving screen starts in a switch: enough to feel it settle, not to notice.
        private const val SETTLE = 0.94f
    }
}

/**
 * Marks a screen the bottom bar reaches, so [NavTransitions] can tell a switch between two of them
 * from going deeper into one.
 */
fun topLevel(): Map<String, Any> = mapOf(TOP_LEVEL to true)

/** Whether this scene is a screen the bottom bar reaches. */
fun Scene<*>.isTopLevel(): Boolean = entries.lastOrNull()?.metadata?.containsKey(TOP_LEVEL) == true

private const val TOP_LEVEL = "goalmaker.topLevel"
