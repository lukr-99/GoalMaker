package com.goalmaker.app.ui.stats

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.LocalFireDepartment
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MediumFlexibleTopAppBar
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.goalmaker.app.R
import com.goalmaker.app.application.planning.StatsDigest
import com.goalmaker.app.ui.components.ScreenTitle
import com.goalmaker.app.ui.lists.SectionHeader
import com.goalmaker.app.ui.theme.AppTheme
import java.time.format.DateTimeFormatter
import kotlin.math.max
import kotlin.math.roundToInt

/** The long view: tasks a week, goals a month, habits and past ratings (docs/stats.md). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatsScreen(viewModel: StatsViewModel, onBack: () -> Unit) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val digest = state.digest

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            MediumFlexibleTopAppBar(
                title = { ScreenTitle(stringResource(R.string.stats_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.settings_back))
                    }
                },
                scrollBehavior = scrollBehavior,
            )
        },
    ) { padding ->
        if (!state.loaded) return@Scaffold
        LazyColumn(
            contentPadding = PaddingValues(horizontal = AppTheme.density.pagePadding.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(AppTheme.density.rowGap.dp),
            modifier = Modifier.fillMaxSize().padding(padding),
        ) {
            if (digest.empty) {
                item("empty") {
                    Text(
                        stringResource(R.string.stats_empty),
                        style = MaterialTheme.typography.bodyLarge,
                        color = AppTheme.colors.textMuted,
                        modifier = Modifier.padding(8.dp),
                    )
                }
                return@LazyColumn
            }

            item("heroes") { Heroes(digest) }

            item("h-weeks") { SectionHeader(stringResource(R.string.stats_tasks_a_week)) }
            item("weeks") { WeekBars(digest.weeks) }

            if (digest.months.isNotEmpty()) {
                item("h-months") { SectionHeader(stringResource(R.string.stats_goals_a_month)) }
                item("months") { MonthBars(digest.months) }
            }

            if (digest.habits.isNotEmpty()) {
                item("h-habits") { SectionHeader(stringResource(R.string.stats_habits)) }
                items(digest.habits, key = { "habit-" + it.id }) { habit -> HabitRow(habit) }
            }

            if (digest.ratings.isNotEmpty()) {
                item("h-ratings") { SectionHeader(stringResource(R.string.stats_ratings)) }
                item("ratings") { RatingChart(digest.ratings) }
            }
        }
    }
}

/** The three numbers worth a glance: what was finished, how the goals ended, how the habits hold. */
@Composable
private fun Heroes(digest: StatsDigest) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        Hero(
            value = digest.done.toString(),
            label = stringResource(R.string.stats_hero_done),
            hint = stringResource(R.string.stats_hero_per_week, oneDecimal(digest.perWeek)),
            modifier = Modifier.weight(1f),
        )
        Hero(
            value = stringResource(R.string.stats_hero_goals_value, digest.goalsHit, digest.goalsTotal),
            label = stringResource(R.string.stats_hero_goals),
            hint = stringResource(R.string.stats_hero_months, digest.months.size),
            modifier = Modifier.weight(1f),
        )
        Hero(
            value = percent(digest.habitRate),
            label = stringResource(R.string.stats_hero_habits),
            hint = stringResource(R.string.stats_hero_habit_count, digest.habits.size),
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun Hero(value: String, label: String, hint: String, modifier: Modifier = Modifier) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .background(AppTheme.colors.surface, AppTheme.shapes.card)
            .padding(vertical = 12.dp, horizontal = 6.dp),
    ) {
        Text(value, style = MaterialTheme.typography.headlineMedium, color = AppTheme.colors.accent, maxLines = 1)
        Text(
            label,
            style = MaterialTheme.typography.labelLarge,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 2.dp),
        )
        Text(
            hint,
            style = MaterialTheme.typography.labelSmall,
            color = AppTheme.colors.textMuted,
            textAlign = TextAlign.Center,
        )
    }
}

/** A bar per week, the week holding today last, with the first and last week named underneath. */
@Composable
private fun WeekBars(weeks: List<StatsDigest.Week>) {
    if (weeks.isEmpty()) return
    val most = max(1, weeks.maxOf { it.done })
    val locale = LocalConfiguration.current.locales[0]
    val format = DateTimeFormatter.ofPattern("d MMM", locale)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(AppTheme.colors.surface, AppTheme.shapes.card)
            .padding(12.dp),
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.Bottom,
            modifier = Modifier.fillMaxWidth().height(96.dp),
        ) {
            weeks.forEach { week ->
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f)) {
                    if (week.done > 0) {
                        Text(week.done.toString(), style = MaterialTheme.typography.labelSmall, color = AppTheme.colors.textMuted, maxLines = 1)
                    }
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height((6 + 62 * week.done / most).dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(if (week.done > 0) AppTheme.colors.accent else AppTheme.colors.outline.copy(alpha = 0.3f)),
                    )
                }
            }
        }
        Row(modifier = Modifier.fillMaxWidth().padding(top = 6.dp)) {
            Text(format.format(weeks.first().start), style = MaterialTheme.typography.labelSmall, color = AppTheme.colors.textMuted)
            Box(Modifier.weight(1f))
            Text(stringResource(R.string.stats_this_week), style = MaterialTheme.typography.labelSmall, color = AppTheme.colors.textMuted)
        }
    }
}

/** A bar per month, filled by the share of its goals that were hit. */
@Composable
private fun MonthBars(months: List<StatsDigest.Month>) {
    val locale = LocalConfiguration.current.locales[0]
    val format = DateTimeFormatter.ofPattern("LLL", locale)
    Row(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier
            .fillMaxWidth()
            .background(AppTheme.colors.surface, AppTheme.shapes.card)
            .padding(12.dp),
    ) {
        months.forEach { month ->
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f)) {
                Box(
                    contentAlignment = Alignment.BottomCenter,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(72.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(AppTheme.colors.outline.copy(alpha = 0.25f)),
                ) {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height((72 * month.fraction).roundToInt().coerceAtLeast(if (month.total > 0) 4 else 0).dp)
                            .background(AppTheme.colors.accent),
                    )
                }
                Text(
                    if (month.total > 0) stringResource(R.string.stats_month_hit, month.hit, month.total) else "",
                    style = MaterialTheme.typography.labelSmall,
                    color = AppTheme.colors.textMuted,
                    maxLines = 1,
                )
                Text(format.format(month.start), style = MaterialTheme.typography.labelSmall, maxLines = 1)
            }
        }
    }
}

@Composable
private fun HabitRow(habit: StatsDigest.Habit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .background(AppTheme.colors.surface, AppTheme.shapes.row)
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        Text(habit.emoji ?: "", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(end = 8.dp))
        Column(Modifier.weight(1f)) {
            Text(habit.name, style = MaterialTheme.typography.bodyLarge, maxLines = 1)
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .padding(top = 2.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(AppTheme.colors.outline.copy(alpha = 0.25f)),
            ) {
                Box(
                    Modifier
                        .fillMaxWidth(habit.rate.toFloat().coerceIn(0f, 1f))
                        .height(6.dp)
                        .background(AppTheme.colors.accent),
                )
            }
            Text(
                stringResource(R.string.stats_habit_met, habit.met, habit.periods, habit.best),
                style = MaterialTheme.typography.labelSmall,
                color = AppTheme.colors.textMuted,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
        Column(horizontalAlignment = Alignment.End, modifier = Modifier.padding(start = 8.dp)) {
            Text(percent(habit.rate), style = MaterialTheme.typography.titleMedium, color = AppTheme.colors.accent)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.LocalFireDepartment, contentDescription = null, tint = AppTheme.colors.textMuted, modifier = Modifier.size(14.dp))
                Text(
                    habit.streak.toString(),
                    style = MaterialTheme.typography.labelSmall,
                    color = AppTheme.colors.textMuted,
                    modifier = Modifier.padding(start = 2.dp),
                )
            }
        }
    }
}

/** Mood and energy, review by review, on the 1 to 5 the review asked for. */
@Composable
private fun RatingChart(ratings: List<StatsDigest.Rating>) {
    val accent = AppTheme.colors.accent
    // The two lines must not be the same colour: Track paints its accent and its primary the same lime.
    val energy = AppTheme.colors.primary.takeIf { it != accent } ?: AppTheme.colors.textMuted
    val grid = AppTheme.colors.outline.copy(alpha = 0.3f)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(AppTheme.colors.surface, AppTheme.shapes.card)
            .padding(12.dp),
    ) {
        Canvas(Modifier.fillMaxWidth().height(120.dp)) {
            val stepX = if (ratings.size > 1) size.width / (ratings.size - 1) else 0f
            val top = 8f
            val bottom = size.height - 8f
            fun point(index: Int, value: Int): Offset {
                val x = if (ratings.size > 1) index * stepX else size.width / 2
                val y = bottom - (value - 1).coerceIn(0, 4) / 4f * (bottom - top)
                return Offset(x, y)
            }
            listOf(1, 3, 5).forEach { line ->
                val y = point(0, line).y
                drawLine(grid, Offset(0f, y), Offset(size.width, y), strokeWidth = 1.dp.toPx())
            }
            listOf(accent to ratings.map { it.mood }, energy to ratings.map { it.energy }).forEach { (color, values) ->
                var last: Offset? = null
                values.forEachIndexed { index, value ->
                    if (value == null) {
                        last = null
                        return@forEachIndexed
                    }
                    val here = point(index, value)
                    last?.let { drawLine(color, it, here, strokeWidth = 3.dp.toPx()) }
                    drawCircle(color, radius = 4.dp.toPx(), center = here)
                    last = here
                }
            }
        }
        Row(modifier = Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Legend(accent, stringResource(R.string.reviews_mood))
            Legend(energy, stringResource(R.string.reviews_energy))
        }
    }
}

@Composable
private fun Legend(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(10.dp).clip(RoundedCornerShape(5.dp)).background(color))
        Text(label, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(start = 6.dp))
    }
}

private fun percent(fraction: Double): String = "${(fraction.coerceIn(0.0, 1.0) * 100).roundToInt()}%"

private fun oneDecimal(value: Double): String = "%.1f".format(value)
