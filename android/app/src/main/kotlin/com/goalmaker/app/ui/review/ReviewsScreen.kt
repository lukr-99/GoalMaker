package com.goalmaker.app.ui.review

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.PlayArrow
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
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.goalmaker.app.R
import com.goalmaker.app.application.planning.ReviewItem
import com.goalmaker.app.application.planning.ReviewRules
import com.goalmaker.app.ui.components.ScreenTitle
import com.goalmaker.app.ui.lists.SectionHeader
import com.goalmaker.app.ui.theme.AppTheme
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/** The reviews waiting to be written and the ones already written (docs/reviews.md). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReviewsScreen(viewModel: ReviewsViewModel, onBack: () -> Unit, onOpen: (String, LocalDate) -> Unit) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val written = state.past.map { it.kind to it.periodStart }.toSet()

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            MediumFlexibleTopAppBar(
                title = { ScreenTitle(stringResource(R.string.reviews_title)) },
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
            item("h-now") { SectionHeader(stringResource(R.string.reviews_to_write)) }
            item("last-week") {
                StartRow(
                    title = stringResource(R.string.reviews_last_week),
                    subtitle = periodText(ReviewRules.WEEKLY, state.lastWeekStart),
                    done = ReviewRules.WEEKLY to state.lastWeekStart in written,
                    onClick = { onOpen(ReviewRules.WEEKLY, state.lastWeekStart) },
                )
            }
            item("this-week") {
                StartRow(
                    title = stringResource(R.string.reviews_this_week),
                    subtitle = periodText(ReviewRules.WEEKLY, state.weekStart),
                    done = ReviewRules.WEEKLY to state.weekStart in written,
                    onClick = { onOpen(ReviewRules.WEEKLY, state.weekStart) },
                )
            }
            item("last-month") {
                StartRow(
                    title = stringResource(R.string.reviews_last_month),
                    subtitle = periodText(ReviewRules.MONTHLY, state.lastMonthStart),
                    done = ReviewRules.MONTHLY to state.lastMonthStart in written,
                    onClick = { onOpen(ReviewRules.MONTHLY, state.lastMonthStart) },
                )
            }
            item("this-month") {
                StartRow(
                    title = stringResource(R.string.reviews_this_month),
                    subtitle = periodText(ReviewRules.MONTHLY, state.monthStart),
                    done = ReviewRules.MONTHLY to state.monthStart in written,
                    onClick = { onOpen(ReviewRules.MONTHLY, state.monthStart) },
                )
            }
            if (state.past.isNotEmpty()) {
                item("h-past") { SectionHeader(stringResource(R.string.reviews_past)) }
                items(state.past, key = { it.id }) { review ->
                    PastRow(review, onOpen = { onOpen(review.kind, review.periodStart) }, onDelete = { viewModel.delete(review.id) })
                }
            } else {
                item("empty") {
                    Text(
                        stringResource(R.string.reviews_empty),
                        style = MaterialTheme.typography.bodyLarge,
                        color = AppTheme.colors.textMuted,
                        modifier = Modifier.padding(8.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun StartRow(title: String, subtitle: String, done: Boolean, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .background(AppTheme.colors.surface, AppTheme.shapes.row)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = AppTheme.colors.textMuted)
        }
        Text(
            stringResource(if (done) R.string.reviews_written else R.string.reviews_start),
            style = MaterialTheme.typography.labelLarge,
            color = AppTheme.colors.accent,
        )
        Icon(Icons.Outlined.PlayArrow, contentDescription = null, tint = AppTheme.colors.accent, modifier = Modifier.padding(start = 6.dp).size(18.dp))
    }
}

@Composable
private fun PastRow(review: ReviewItem, onOpen: () -> Unit, onDelete: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .background(AppTheme.colors.surface, AppTheme.shapes.row)
            .clickable(onClick = onOpen)
            .padding(start = 12.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(periodText(review.kind, review.periodStart), style = MaterialTheme.typography.bodyLarge)
            Text(
                listOfNotNull(
                    review.mood?.let { stringResource(R.string.reviews_mood_short, it) },
                    review.energy?.let { stringResource(R.string.reviews_energy_short, it) },
                    review.reflections.count { it.answer.isNotBlank() }.takeIf { it > 0 }?.let { stringResource(R.string.reviews_answers_short, it) },
                ).joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = AppTheme.colors.textMuted,
            )
        }
        IconButton(onClick = onDelete) {
            Icon(Icons.Outlined.Delete, contentDescription = stringResource(R.string.reviews_delete), tint = AppTheme.colors.danger)
        }
    }
}

/** "14 to 20 September" for a week, "September 2026" for a month, "2026" for a year. */
@Composable
private fun periodText(kind: String, start: LocalDate): String {
    val locale = LocalConfiguration.current.locales[0]
    return when (kind) {
        ReviewRules.MONTHLY -> DateTimeFormatter.ofPattern("LLLL yyyy", locale).format(start)
        ReviewRules.YEARLY -> start.year.toString()
        else -> {
            val end = start.plusDays(6)
            val first = DateTimeFormatter.ofPattern(if (start.month == end.month) "d" else "d MMM", locale).format(start)
            stringResource(R.string.goals_range, first, DateTimeFormatter.ofPattern("d MMM", locale).format(end))
        }
    }
}
