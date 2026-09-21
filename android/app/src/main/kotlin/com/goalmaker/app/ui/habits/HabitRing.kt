package com.goalmaker.app.ui.habits

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.goalmaker.app.ui.components.ProgressRing
import com.goalmaker.app.ui.theme.AppTheme
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlinx.coroutines.launch

/**
 * A habit's ring (design spec, "Habit done"): it fills with a spring, and when it fills up a small
 * burst of rays leaves it while it pops. Both are skipped under reduce motion.
 */
@Composable
fun HabitRing(
    fraction: Float,
    modifier: Modifier = Modifier,
    size: Dp = 44.dp,
    stroke: Dp = 4.dp,
    color: Color = AppTheme.colors.accent,
    content: @Composable () -> Unit = {},
) {
    val reduceMotion = AppTheme.reduceMotion
    val accent = color
    val emphasized = AppTheme.motion.emphasized
    val shown by animateFloatAsState(
        targetValue = fraction.coerceIn(0f, 1f),
        animationSpec = if (reduceMotion) tween(0) else spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
        label = "habit ring",
    )
    val burst = remember { Animatable(0f) }
    val pop = remember { Animatable(1f) }
    // Only a ring that fills while it is shown bursts, not one that was full already.
    var before by remember { mutableStateOf<Float?>(null) }
    LaunchedEffect(fraction) {
        val was = before
        before = fraction
        if (was != null && was < 1f && fraction >= 1f && !reduceMotion) {
            launch {
                burst.snapTo(0f)
                burst.animateTo(1f, tween(emphasized))
                burst.snapTo(0f)
            }
            launch {
                pop.snapTo(1f)
                pop.animateTo(1.14f, tween(90))
                pop.animateTo(1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium))
            }
        }
    }
    ProgressRing(
        fraction = shown,
        size = size,
        stroke = stroke,
        color = color,
        modifier = modifier
            .graphicsLayer {
                scaleX = pop.value
                scaleY = pop.value
            }
            .drawBehind {
                val progress = burst.value
                if (progress <= 0f) return@drawBehind
                val radius = this.size.minDimension / 2
                val inner = radius * (1.05f + 0.35f * progress)
                val outer = inner + radius * 0.35f * (1f - progress)
                val alpha = 1f - progress
                repeat(RAYS) { index ->
                    val angle = 2 * PI * index / RAYS - PI / 2
                    val direction = Offset(cos(angle).toFloat(), sin(angle).toFloat())
                    drawLine(
                        color = accent.copy(alpha = alpha),
                        start = center + direction * inner,
                        end = center + direction * outer,
                        strokeWidth = stroke.toPx() * 0.75f,
                        cap = StrokeCap.Round,
                    )
                }
            },
        content = content,
    )
}

private const val RAYS = 8
