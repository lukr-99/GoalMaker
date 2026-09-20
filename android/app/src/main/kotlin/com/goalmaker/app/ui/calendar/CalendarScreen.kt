package com.goalmaker.app.ui.calendar

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.ChevronLeft
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.NotificationsNone
import androidx.compose.material.icons.outlined.Today
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
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.goalmaker.app.R
import com.goalmaker.app.application.planning.CalendarDay
import com.goalmaker.app.application.planning.CalendarRules
import com.goalmaker.app.application.planning.TaskItem
import com.goalmaker.app.application.planning.TaskState
import com.goalmaker.app.ui.components.ChoiceChip
import com.goalmaker.app.ui.components.ScreenTitle
import com.goalmaker.app.ui.lists.SectionHeader
import com.goalmaker.app.ui.theme.AppTheme
import java.time.DayOfWeek
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle

/** A week or a month of planned tasks, deadlines and reminders (docs/calendar.md). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalendarScreen(viewModel: CalendarViewModel, onBack: () -> Unit, onOpenTask: (String) -> Unit) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val locale = LocalConfiguration.current.locales[0]

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            MediumFlexibleTopAppBar(
                title = { ScreenTitle(stringResource(R.string.calendar_title)) },
                subtitle = { Text(period(state, locale), maxLines = 1) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.settings_back))
                    }
                },
                actions = {
                    IconButton(onClick = viewModel::back) {
                        Icon(Icons.Outlined.ChevronLeft, contentDescription = stringResource(R.string.calendar_back))
                    }
                    IconButton(onClick = { viewModel.today(true) }) {
                        Icon(Icons.Outlined.Today, contentDescription = stringResource(R.string.calendar_today))
                    }
                    IconButton(onClick = viewModel::forward) {
                        Icon(Icons.Outlined.ChevronRight, contentDescription = stringResource(R.string.calendar_forward))
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
            item("kind") {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ChoiceChip(
                        selected = state.kind == CalendarRules.WEEK,
                        onClick = { viewModel.show(CalendarRules.WEEK) },
                        label = stringResource(R.string.calendar_week),
                    )
                    ChoiceChip(
                        selected = state.kind == CalendarRules.MONTH,
                        onClick = { viewModel.show(CalendarRules.MONTH) },
                        label = stringResource(R.string.calendar_month),
                    )
                }
            }

            item("weekdays") { WeekdayRow(locale) }

            state.weeks.forEachIndexed { index, week ->
                item("week-$index") {
                    Row(Modifier.fillMaxWidth()) {
                        week.forEach { day ->
                            DayCell(
                                day = day,
                                today = day.day == state.today,
                                inPeriod = state.kind == CalendarRules.WEEK || day.day.month == state.anchor.month,
                                selected = day.day == state.selected,
                                onClick = { viewModel.open(day.day) },
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }
            }

            val open = state.openDay
            if (open == null) {
                item("hint") {
                    Text(
                        stringResource(R.string.calendar_pick_a_day),
                        style = MaterialTheme.typography.bodyMedium,
                        color = AppTheme.colors.textMuted,
                        modifier = Modifier.padding(8.dp),
                    )
                }
            } else {
                item("day-header") {
                    SectionHeader(DateTimeFormatter.ofPattern("EEEE d MMMM", locale).format(open.day))
                }
                if (open.empty) {
                    item("day-empty") {
                        Text(
                            stringResource(R.string.calendar_day_empty),
                            style = MaterialTheme.typography.bodyMedium,
                            color = AppTheme.colors.textMuted,
                            modifier = Modifier.padding(8.dp),
                        )
                    }
                }
                open.planned.forEach { task ->
                    item("planned-" + task.id) { DayRow(task, R.string.calendar_planned, onOpenTask) }
                }
                open.deadlines.forEach { task ->
                    item("deadline-" + task.id) { DayRow(task, R.string.calendar_deadline, onOpenTask) }
                }
                open.repeats.forEach { task ->
                    item("repeat-" + task.id) { DayRow(task, R.string.calendar_repeat, onOpenTask) }
                }
                if (open.reminders > 0) {
                    item("reminders") {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 8.dp)) {
                            Icon(
                                Icons.Outlined.NotificationsNone,
                                contentDescription = null,
                                tint = AppTheme.colors.textMuted,
                                modifier = Modifier.size(16.dp),
                            )
                            Text(
                                stringResource(R.string.calendar_reminders, open.reminders),
                                style = MaterialTheme.typography.bodySmall,
                                color = AppTheme.colors.textMuted,
                                modifier = Modifier.padding(start = 6.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun WeekdayRow(locale: java.util.Locale) {
    Row(Modifier.fillMaxWidth()) {
        DayOfWeek.entries.forEach { weekday ->
            Text(
                weekday.getDisplayName(TextStyle.NARROW, locale),
                style = MaterialTheme.typography.labelSmall,
                color = AppTheme.colors.textMuted,
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

/** One day of the grid: what it holds as a number, with today outlined and the day open filled. */
@Composable
private fun DayCell(
    day: CalendarDay,
    today: Boolean,
    inPeriod: Boolean,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val background = when {
        selected -> AppTheme.colors.accent
        day.empty -> AppTheme.colors.surface.copy(alpha = if (inPeriod) 1f else 0.4f)
        else -> AppTheme.colors.surface
    }
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = modifier
            .padding(2.dp)
            .aspectRatio(0.9f)
            .clip(RoundedCornerShape(10.dp))
            .background(background)
            .clickable(onClick = onClick)
            .padding(2.dp),
    ) {
        Text(
            day.day.dayOfMonth.toString(),
            style = MaterialTheme.typography.labelLarge,
            fontWeight = if (today) FontWeight.Bold else FontWeight.Normal,
            color = when {
                selected -> AppTheme.colors.onAccent
                today -> AppTheme.colors.accent
                inPeriod -> AppTheme.colors.text
                else -> AppTheme.colors.textMuted
            },
        )
        if (day.count > 0) {
            Box(
                Modifier
                    .padding(top = 2.dp)
                    .height(4.dp)
                    .size(width = (6 + 4 * minOf(day.count, 4)).dp, height = 4.dp)
                    .clip(CircleShape)
                    .background(if (selected) AppTheme.colors.onAccent else AppTheme.colors.accent),
            )
        }
    }
}

@Composable
private fun DayRow(task: TaskItem, label: Int, onOpenTask: (String) -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .background(AppTheme.colors.surface, AppTheme.shapes.row)
            .clickable { onOpenTask(task.id) }
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                task.title,
                style = MaterialTheme.typography.bodyLarge,
                color = if (task.state == TaskState.DONE) AppTheme.colors.textMuted else AppTheme.colors.text,
                maxLines = 2,
            )
            Text(
                listOfNotNull(stringResource(label), task.plannedTime?.toString()).joinToString(" · "),
                style = MaterialTheme.typography.labelSmall,
                color = AppTheme.colors.textMuted,
            )
        }
    }
}

/** "September 2026" for a month, "14 to 20 Sep" for a week. */
@Composable
private fun period(state: CalendarUiState, locale: java.util.Locale): String {
    if (!state.loaded) return ""
    if (state.kind == CalendarRules.MONTH) {
        return DateTimeFormatter.ofPattern("LLLL yyyy", locale).format(state.anchor)
    }
    val first = CalendarRules.start(CalendarRules.WEEK, state.anchor)
    val last = CalendarRules.end(CalendarRules.WEEK, state.anchor)
    val day = DateTimeFormatter.ofPattern("d", locale)
    val dayMonth = DateTimeFormatter.ofPattern("d MMM", locale)
    return stringResource(
        R.string.goals_range,
        if (first.month == last.month) day.format(first) else dayMonth.format(first),
        dayMonth.format(last),
    )
}
