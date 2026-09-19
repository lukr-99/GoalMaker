package com.goalmaker.app.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.goalmaker.app.R
import com.goalmaker.app.ui.theme.AppTheme
import kotlinx.coroutines.launch

/**
 * The GoalMaker mark in the current theme's logo colors (themes.json): the tile, the G and the trend
 * arrow (contracts/design/logo.json). When the theme changes it recolors, the arrow draws itself
 * again and the tile gives a small bounce; with reduce motion the colors just cross-fade. Nothing is
 * drawn where the theme has no mark (previews).
 */
@Composable
fun GoalMakerLogo(modifier: Modifier = Modifier, size: Dp = 48.dp) {
    val logo = AppTheme.logo ?: return
    val mark = logo.mark
    val motion = AppTheme.motion
    val reduced = AppTheme.reduceMotion
    val fade = tween<Color>(if (reduced) motion.quick else motion.emphasized)
    val tile by animateColorAsState(Color(logo.colors.tile), fade, label = "tile")
    val letter by animateColorAsState(Color(logo.colors.letter), fade, label = "letter")
    val arrow by animateColorAsState(Color(logo.colors.arrow), fade, label = "arrow")

    // The arrow's drawn share (0 to 1) and the tile's scale, replayed when the theme changes.
    val drawn = remember { Animatable(1f) }
    val bounce = remember { Animatable(1f) }
    var shown by remember { mutableStateOf(logo.colors) }
    LaunchedEffect(logo.colors) {
        if (shown == logo.colors) return@LaunchedEffect
        shown = logo.colors
        if (reduced) return@LaunchedEffect
        launch {
            drawn.snapTo(0f)
            drawn.animateTo(1f, tween(motion.emphasized * 2, easing = FastOutSlowInEasing))
        }
        launch {
            bounce.snapTo(0.88f)
            bounce.animateTo(1f, spring(dampingRatio = 0.45f, stiffness = 320f))
        }
    }

    val letterPath = remember(mark) { PathParser().parsePathString(mark.letter).toPath() }
    val trendPath = remember(mark) { PathParser().parsePathString(mark.trend).toPath() }
    val headPath = remember(mark) { PathParser().parsePathString(mark.head).toPath() }
    val measure = remember(trendPath) { PathMeasure().apply { setPath(trendPath, false) } }
    val name = stringResource(R.string.app_name)
    Canvas(
        modifier
            .size(size)
            .graphicsLayer {
                scaleX = bounce.value
                scaleY = bounce.value
            }
            .semantics { contentDescription = name },
    ) {
        scale(this.size.minDimension / mark.size, pivot = Offset.Zero) {
            drawRoundRect(tile, size = Size(mark.size, mark.size), cornerRadius = CornerRadius(mark.tileCorner))
            drawPath(letterPath, letter, style = Stroke(mark.stroke, cap = StrokeCap.Round))
            val line = if (drawn.value >= 1f) {
                trendPath
            } else {
                Path().also { part -> measure.getSegment(0f, measure.length * drawn.value, part, true) }
            }
            drawPath(line, arrow, style = Stroke(mark.stroke, cap = StrokeCap.Round, join = StrokeJoin.Round))
            // The head lands as the line reaches it.
            val head = ((drawn.value - HEAD_FROM) / (1f - HEAD_FROM)).coerceIn(0f, 1f)
            if (head > 0f) drawPath(headPath, arrow, alpha = head)
        }
    }
}

private const val HEAD_FROM = 0.8f
