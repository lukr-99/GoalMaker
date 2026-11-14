package com.goalmaker.app.ui.about

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.goalmaker.app.ui.theme.AppTheme

/**
 * The mark's trend line on its own: a line that zigzags up to the top right and ends in an arrow head,
 * drawn across its whole box. It draws itself once as it appears, at the pace of the logo's arrow, and
 * stands still with reduce motion. Decoration only, so screen readers skip it.
 */
@Composable
fun TrendStroke(color: Color, modifier: Modifier = Modifier, width: Dp = 6.dp, delayMillis: Int = 0) {
    val motion = AppTheme.motion
    val reduced = AppTheme.reduceMotion
    val drawn = remember { Animatable(if (reduced) 1f else 0f) }
    LaunchedEffect(reduced) {
        if (reduced) {
            drawn.snapTo(1f)
        } else {
            drawn.animateTo(1f, tween(motion.emphasized * 2, delayMillis = delayMillis, easing = FastOutSlowInEasing))
        }
    }
    Canvas(modifier) {
        val stroke = width.toPx()
        val inset = stroke * 2f
        val w = size.width - inset * 2f
        val h = size.height - inset * 2f
        val points = TREND.map { (x, y) -> Offset(inset + x * w, inset + y * h) }
        val line = Path().apply {
            moveTo(points.first().x, points.first().y)
            points.drop(1).forEach { lineTo(it.x, it.y) }
        }
        val measure = PathMeasure().apply { setPath(line, false) }
        val part = Path().also { measure.getSegment(0f, measure.length * drawn.value, it, true) }
        drawPath(part, color, style = Stroke(stroke, cap = StrokeCap.Round, join = StrokeJoin.Round))
        // The head lands as the line reaches it, along the last leg's direction.
        val head = ((drawn.value - HEAD_FROM) / (1f - HEAD_FROM)).coerceIn(0f, 1f)
        if (head > 0f) {
            val tip = points.last()
            val from = points[points.lastIndex - 1]
            val dx = tip.x - from.x
            val dy = tip.y - from.y
            val length = kotlin.math.hypot(dx, dy)
            val ux = dx / length
            val uy = dy / length
            val reach = stroke * 2.6f
            val arrow = Path().apply {
                moveTo(tip.x + ux * stroke, tip.y + uy * stroke)
                lineTo(tip.x - ux * reach - uy * reach * 0.8f, tip.y - uy * reach + ux * reach * 0.8f)
                lineTo(tip.x - ux * reach + uy * reach * 0.8f, tip.y - uy * reach - ux * reach * 0.8f)
                close()
            }
            drawPath(arrow, color, alpha = head)
        }
    }
}

// The line's corners on a unit box, from the bottom left up to the top right.
private val TREND = listOf(0f to 1f, 0.28f to 0.58f, 0.48f to 0.76f, 0.94f to 0.06f)
private const val HEAD_FROM = 0.8f
