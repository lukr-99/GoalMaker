package com.goalmaker.app.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import com.goalmaker.app.ui.theme.AppTheme
import kotlinx.coroutines.delay

/**
 * The launch moment (the owner's idea, 2026-09-19): the mark in the theme's colors draws its arrow in
 * the middle of the screen while the app loads underneath, then fades away. Once per start, a tap skips
 * it, and reduce motion leaves it out.
 */
@Composable
fun LaunchIntro(content: @Composable () -> Unit) {
    val reduced = AppTheme.reduceMotion
    val motion = AppTheme.motion
    var done by rememberSaveable { mutableStateOf(reduced) }
    Box(Modifier.fillMaxSize()) {
        content()
        if (!done) {
            val alpha = remember { Animatable(1f) }
            LaunchedEffect(Unit) {
                // The logo's draw (two emphasized beats) and a short hold, then the fade.
                delay((motion.emphasized * 2 + motion.standard).toLong())
                alpha.animateTo(0f, tween(motion.standard))
                done = true
            }
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        this.alpha = alpha.value
                        val grow = 1f + (1f - alpha.value) * 0.06f
                        scaleX = grow
                        scaleY = grow
                    }
                    .background(MaterialTheme.colorScheme.background)
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { done = true },
            ) {
                GoalMakerLogo(size = 96.dp, intro = true)
            }
        }
    }
}
