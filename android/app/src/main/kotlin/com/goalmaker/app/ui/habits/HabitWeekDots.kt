package com.goalmaker.app.ui.habits

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.goalmaker.app.R
import com.goalmaker.app.application.planning.HabitDot
import com.goalmaker.app.ui.theme.AppTheme
import java.time.LocalDate
import java.time.format.TextStyle

/**
 * The week's dots on a habit card (contracts/vectors/habits.json, dots): the seven days up to [today],
 * each with its weekday's letter. Met fills with the accent, missed is a faint dot, skipped a dashed
 * ring, paused a faint ring, over a limit the danger colour, today an accent ring until it is met.
 * Screen readers hear the count of each instead of seven dots.
 */
@Composable
fun HabitWeekDots(dots: List<HabitDot>, today: LocalDate, modifier: Modifier = Modifier) {
    val locale = LocalConfiguration.current.locales[0]
    val colors = AppTheme.colors
    val description = stringResource(
        R.string.habits_dots,
        dots.count { it == HabitDot.MET },
        dots.count { it == HabitDot.MISSED || it == HabitDot.OVER },
        dots.count { it == HabitDot.SKIPPED },
    )
    Row(
        horizontalArrangement = Arrangement.spacedBy(5.dp),
        modifier = modifier.clearAndSetSemantics { contentDescription = description },
    ) {
        dots.forEachIndexed { index, dot ->
            val day = today.minusDays((dots.size - 1 - index).toLong())
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Canvas(Modifier.size(10.dp)) {
                    val radius = size.minDimension / 2
                    val ring = 1.5.dp.toPx()
                    when (dot) {
                        HabitDot.MET -> drawCircle(colors.accent, radius)
                        HabitDot.MISSED -> drawCircle(colors.outline.copy(alpha = 0.3f), radius)
                        HabitDot.OVER -> drawCircle(colors.danger, radius)
                        HabitDot.OPEN -> drawCircle(colors.accent, radius - ring, style = Stroke(ring * 1.3f))
                        HabitDot.SKIPPED -> drawCircle(
                            colors.outline,
                            radius - ring / 2,
                            style = Stroke(ring, pathEffect = PathEffect.dashPathEffect(floatArrayOf(ring * 1.6f, ring * 1.2f))),
                        )
                        HabitDot.PAUSED -> drawCircle(colors.outline.copy(alpha = 0.5f), radius - ring / 2, style = Stroke(ring))
                        HabitDot.NONE -> drawCircle(colors.outline.copy(alpha = 0.3f), 1.dp.toPx(), center = Offset(size.width / 2, size.height / 2))
                    }
                }
                Text(
                    day.dayOfWeek.getDisplayName(TextStyle.NARROW, locale),
                    fontSize = 9.sp,
                    color = if (day == today) colors.accent else colors.textMuted,
                )
            }
        }
    }
}
