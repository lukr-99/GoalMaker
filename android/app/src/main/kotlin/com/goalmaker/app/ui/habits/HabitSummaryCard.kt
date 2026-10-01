package com.goalmaker.app.ui.habits

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.goalmaker.app.R
import com.goalmaker.app.ui.theme.AppTheme
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

/**
 * The Habits screen's summary on a hero card (the habits prototype, option B): the day, how many of
 * today's habits are done as a big number, the longest streak, and a ring of how far the day has got.
 */
@Composable
fun HabitSummaryCard(summary: HabitSummary, today: LocalDate, modifier: Modifier = Modifier) {
    val colors = AppTheme.colors
    val locale = LocalConfiguration.current.locales[0]
    val best = summary.best
    val bestText = if (best != null) {
        stringResource(R.string.habits_summary_best, listOfNotNull(best.habit.emoji, best.habit.name).joinToString(" "), streakText(best).orEmpty())
    } else {
        stringResource(R.string.habits_summary_no_streak)
    }
    val description = pluralStringResource(R.plurals.habits_summary_description, summary.total, summary.done, summary.total) + ". " + bestText
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .background(colors.hero, AppTheme.shapes.card)
            .padding(horizontal = 16.dp, vertical = 14.dp)
            .clearAndSetSemantics { contentDescription = description },
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                AppTheme.headline(DateTimeFormatter.ofPattern("EEEE d MMMM", locale).format(today)).uppercase(locale),
                style = MaterialTheme.typography.labelMedium,
                color = colors.onHero,
            )
            Row(verticalAlignment = Alignment.Bottom, modifier = Modifier.padding(top = 4.dp)) {
                Text(summary.done.toString(), style = AppTheme.type.number.copy(fontSize = 46.sp, lineHeight = 48.sp), color = colors.heroAccent)
                Text(
                    stringResource(R.string.habits_summary_count, summary.total),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = colors.onHero,
                    modifier = Modifier.padding(start = 6.dp, bottom = 8.dp),
                )
            }
            Text(bestText, style = MaterialTheme.typography.bodySmall, color = colors.onHero, modifier = Modifier.padding(top = 2.dp))
        }
        HabitRing(
            fraction = summary.share.toFloat(),
            size = 72.dp,
            stroke = 9.dp,
            color = colors.heroAccent,
            track = colors.onHero.copy(alpha = 0.22f),
            modifier = Modifier.padding(start = 12.dp),
        ) {
            Text(
                stringResource(R.string.habits_summary_percent, (summary.share * 100).roundToInt()),
                style = AppTheme.type.number.copy(fontSize = 16.sp),
                color = colors.heroAccent,
            )
        }
    }
}
