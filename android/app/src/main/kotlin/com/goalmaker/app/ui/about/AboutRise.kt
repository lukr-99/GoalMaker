package com.goalmaker.app.ui.about

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import com.goalmaker.app.ui.theme.AppTheme

/**
 * One block of the About page, which fades in and rises a little into place when the page opens, a
 * beat after the block above it ([order]). With reduce motion it is simply there.
 */
@Composable
fun AboutRise(order: Int, content: @Composable () -> Unit) {
    val motion = AppTheme.motion
    val reduced = AppTheme.reduceMotion
    val shown = remember { Animatable(if (reduced) 1f else 0f) }
    LaunchedEffect(reduced) {
        if (reduced) {
            shown.snapTo(1f)
        } else {
            shown.animateTo(1f, tween(motion.standard, delayMillis = order * STAGGER, easing = FastOutSlowInEasing))
        }
    }
    Box(
        Modifier.graphicsLayer {
            alpha = shown.value
            translationY = (1f - shown.value) * RISE.toPx()
        },
    ) { content() }
}

private const val STAGGER = 70
private val RISE = 18.dp
