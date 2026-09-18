package com.goalmaker.app.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.goalmaker.app.ui.theme.AppTheme

/**
 * The theme's checkbox: its corner shape (square for Track, round for Electric), the accent fill,
 * and a check that draws itself (docs/design/spec.md, "Task done"). With reduce motion it just
 * appears. 48 dp touch target.
 */
@Composable
fun GoalMakerCheckbox(checked: Boolean, onCheckedChange: (Boolean) -> Unit, modifier: Modifier = Modifier) {
    val colors = AppTheme.colors
    val shape = AppTheme.shapes.checkbox
    val duration = if (AppTheme.reduceMotion) 0 else AppTheme.motion.standard
    val fill by animateFloatAsState(if (checked) 1f else 0f, tween(duration), label = "fill")
    val draw by animateFloatAsState(if (checked) 1f else 0f, tween(duration, delayMillis = duration / 3), label = "check")
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .minimumInteractiveComponentSize()
            .toggleable(value = checked, role = Role.Checkbox, onValueChange = onCheckedChange),
    ) {
        Canvas(Modifier.size(22.dp)) {
            val stroke = 2.dp.toPx()
            val outline = shape.createOutline(size, layoutDirection, this)
            if (fill < 1f) drawOutline(outline, colors.outline, style = Stroke(stroke))
            if (fill > 0f) drawOutline(outline, colors.accent, alpha = fill)
            if (draw > 0f) {
                val check = Path().apply {
                    moveTo(size.width * 0.24f, size.height * 0.53f)
                    lineTo(size.width * 0.42f, size.height * 0.71f)
                    lineTo(size.width * 0.77f, size.height * 0.33f)
                }
                val measure = PathMeasure().apply { setPath(check, false) }
                val partial = Path()
                measure.getSegment(0f, measure.length * draw, partial, true)
                drawPath(partial, colors.onAccent, style = Stroke(stroke * 1.2f, cap = StrokeCap.Round, join = StrokeJoin.Round))
            }
        }
    }
}
