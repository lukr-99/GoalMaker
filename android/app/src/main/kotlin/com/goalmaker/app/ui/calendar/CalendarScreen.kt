package com.goalmaker.app.ui.calendar

import android.content.ClipData
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.draganddrop.dragAndDropSource
import androidx.compose.foundation.draganddrop.dragAndDropTarget
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draganddrop.DragAndDropEvent
import androidx.compose.ui.draganddrop.DragAndDropTarget
import androidx.compose.ui.draganddrop.DragAndDropTransferData
import androidx.compose.ui.draganddrop.toAndroidDragEvent
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.goalmaker.app.R
import com.goalmaker.app.application.planning.CalendarDay
import com.goalmaker.app.application.planning.CalendarRules
import com.goalmaker.app.application.planning.ProjectItem
import com.goalmaker.app.application.planning.TaskItem
import com.goalmaker.app.application.planning.TaskState
import com.goalmaker.app.ui.components.ChoiceChip
import com.goalmaker.app.ui.components.GoalMakerCheckbox
import com.goalmaker.app.ui.components.ProjectChip
import com.goalmaker.app.ui.components.ScreenTitle
import com.goalmaker.app.ui.habits.AmountDialog
import com.goalmaker.app.ui.habits.HabitCard
import com.goalmaker.app.ui.habits.HabitRow
import com.goalmaker.app.ui.habits.HabitSheet
import com.goalmaker.app.ui.lists.ListFilterRow
import com.goalmaker.app.ui.lists.SectionHeader
import com.goalmaker.app.ui.nav.AppMark
import com.goalmaker.app.ui.nav.PlaceNavigationIcon
import com.goalmaker.app.ui.theme.AppTheme
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

/** A week or a month of planned tasks, deadlines and reminders (docs/calendar.md). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalendarScreen(
    viewModel: CalendarViewModel,
    onOpenTask: (String) -> Unit,
    actions: @Composable () -> Unit,
    onBack: (() -> Unit)? = null,
    onOpenProject: ((String) -> Unit)? = null,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val locale = LocalConfiguration.current.locales[0]
    val scope = rememberCoroutineScope()
    var logging by remember { mutableStateOf<HabitRow?>(null) }
    var habitMenu by remember { mutableStateOf<String?>(null) }

    // A habit's button on the open day: undo a skip or a fail, or check in; an amount asks for its value.
    fun checkIn(row: HabitRow, day: LocalDate) {
        when {
            row.skipped -> viewModel.skipHabit(row.habit.id, day, false)
            row.failed -> viewModel.failHabit(row.habit.id, day, false)
            else -> scope.launch { if (!viewModel.tapHabit(row.habit.id, day)) logging = row }
        }
    }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            MediumFlexibleTopAppBar(
                title = { ScreenTitle(stringResource(R.string.calendar_title)) },
                subtitle = { Text(period(state, locale), maxLines = 1) },
                navigationIcon = { PlaceNavigationIcon(onBack) { AppMark() } },
                actions = { actions() },
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
            // The period's own controls sit over it, since the top bar carries what every place does.
            item("kind") {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
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
                    Spacer(Modifier.weight(1f))
                    IconButton(onClick = viewModel::back) {
                        Icon(Icons.Outlined.ChevronLeft, contentDescription = stringResource(R.string.calendar_back))
                    }
                    IconButton(onClick = { viewModel.today(true) }) {
                        Icon(Icons.Outlined.Today, contentDescription = stringResource(R.string.calendar_today))
                    }
                    IconButton(onClick = viewModel::forward) {
                        Icon(Icons.Outlined.ChevronRight, contentDescription = stringResource(R.string.calendar_forward))
                    }
                }
            }

            // The area and tag filter narrows what the grid counts and the day lists, as on the lists.
            item("filter") {
                ListFilterRow(state.filter, onArea = viewModel::filterByArea, onTag = viewModel::filterByTag)
            }

            item("weekdays") { WeekdayRow(locale) }

            state.weeks.forEachIndexed { index, week ->
                item("week-$index") {
                    // A week's days share one height, the tallest a cell needs at the system's text size.
                    Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
                        week.forEach { day ->
                            DayCell(
                                day = day,
                                today = day.day == state.today,
                                inPeriod = state.kind == CalendarRules.WEEK || day.day.month == state.anchor.month,
                                selected = day.day == state.selected,
                                onClick = { viewModel.open(day.day) },
                                onDropTask = { id -> viewModel.plan(id, day.day) },
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
                item("drag-hint") {
                    Text(
                        stringResource(R.string.calendar_drag_hint),
                        style = MaterialTheme.typography.labelSmall,
                        color = AppTheme.colors.textMuted,
                        modifier = Modifier.padding(start = 8.dp),
                    )
                }
                if (state.dayHabits.isNotEmpty()) {
                    item("h-habits") { SectionHeader(stringResource(R.string.calendar_habits)) }
                    state.dayHabits.forEach { row ->
                        item("habit-" + row.habit.id) {
                            HabitCard(
                                row = row,
                                today = open.day,
                                full = false,
                                onCheckIn = { checkIn(row, open.day) },
                                onMenu = { habitMenu = row.habit.id },
                                onSkip = { viewModel.skipHabit(row.habit.id, open.day, true) },
                            )
                        }
                    }
                }
                val onDone = { task: TaskItem, done: Boolean -> viewModel.setDone(task, done, open.day) }
                open.planned.forEach { task ->
                    item("planned-" + task.id) {
                        DayRow(task, R.string.calendar_planned, onOpenTask, state.projectOf(task), onOpenProject, draggable = true, onDone = onDone)
                    }
                }
                open.deadlines.forEach { task ->
                    item("deadline-" + task.id) { DayRow(task, R.string.calendar_deadline, onOpenTask, state.projectOf(task), onOpenProject, onDone = onDone) }
                }
                open.repeats.forEach { task ->
                    item("repeat-" + task.id) { DayRow(task, R.string.calendar_repeat, onOpenTask, state.projectOf(task), onOpenProject) }
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

    val day = state.openDay?.day
    logging?.let { row ->
        if (day != null) AmountDialog(row.habit, onLog = { amount -> viewModel.checkInHabit(row.habit.id, day, amount) }, onDismiss = { logging = null })
    }
    // A habit's menu on the open day: check in, skip, fail or clear there; pausing and editing stay on Habits.
    habitMenu?.let { id ->
        val row = state.dayHabits.firstOrNull { it.habit.id == id }
        if (row != null && day != null) {
            HabitSheet(
                row = row,
                onDismiss = { habitMenu = null },
                onCheckIn = { checkIn(row, day) },
                onLog = { logging = row },
                onSkip = { skipped -> viewModel.skipHabit(id, day, skipped) },
                onFail = { failed -> viewModel.failHabit(id, day, failed) },
                onClear = { viewModel.clearHabit(id, day) },
            )
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

/**
 * One day of the grid: what it holds as a number, with today outlined and the day open filled. A task
 * dragged from the day's list below lands on it, which plans it for that day (docs/calendar.md).
 * A screen reader hears the whole date and what is on it. The cell keeps the grid's shape and grows
 * taller when large text needs the room.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun DayCell(
    day: CalendarDay,
    today: Boolean,
    inPeriod: Boolean,
    selected: Boolean,
    onClick: () -> Unit,
    onDropTask: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var hovered by remember { mutableStateOf(false) }
    val target = remember(day.day) {
        object : DragAndDropTarget {
            override fun onDrop(event: DragAndDropEvent): Boolean {
                hovered = false
                val id = event.toAndroidDragEvent().clipData?.getItemAt(0)?.text?.toString().orEmpty()
                if (id.isEmpty()) return false
                onDropTask(id)
                return true
            }

            override fun onEntered(event: DragAndDropEvent) {
                hovered = true
            }

            override fun onExited(event: DragAndDropEvent) {
                hovered = false
            }

            override fun onEnded(event: DragAndDropEvent) {
                hovered = false
            }
        }
    }
    val background = when {
        hovered -> AppTheme.colors.accent.copy(alpha = 0.4f)
        selected -> AppTheme.colors.accent
        day.empty -> AppTheme.colors.surface.copy(alpha = if (inPeriod) 1f else 0.4f)
        else -> AppTheme.colors.surface
    }
    val description = dayDescription(day, today, selected)
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = modifier
            .padding(2.dp)
            .fillMaxHeight()
            .layout { measurable, constraints ->
                val least = (constraints.maxWidth / CELL_RATIO).roundToInt().coerceIn(constraints.minHeight, constraints.maxHeight)
                val placeable = measurable.measure(constraints.copy(minWidth = constraints.maxWidth, minHeight = least))
                layout(placeable.width, placeable.height) { placeable.place(0, 0) }
            }
            .clip(RoundedCornerShape(10.dp))
            .background(background)
            .clickable(onClick = onClick)
            .semantics { contentDescription = description }
            .dragAndDropTarget(shouldStartDragAndDrop = { true }, target = target)
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
            // The description already says the date.
            modifier = Modifier.clearAndSetSemantics {},
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

/** "Saturday 3 October, today, 2 planned, 1 due" for a cell, said where a sighted owner sees the bar. */
@Composable
private fun dayDescription(day: CalendarDay, today: Boolean, selected: Boolean): String {
    val locale = LocalConfiguration.current.locales[0]
    val parts = mutableListOf(DateTimeFormatter.ofPattern("EEEE d MMMM", locale).format(day.day))
    if (today) parts += stringResource(R.string.calendar_cell_today)
    if (selected) parts += stringResource(R.string.calendar_cell_open)
    if (day.empty) parts += stringResource(R.string.calendar_cell_empty)
    if (day.planned.isNotEmpty()) parts += pluralStringResource(R.plurals.calendar_cell_planned, day.planned.size, day.planned.size)
    if (day.deadlines.isNotEmpty()) parts += pluralStringResource(R.plurals.calendar_cell_due, day.deadlines.size, day.deadlines.size)
    if (day.repeats.isNotEmpty()) parts += pluralStringResource(R.plurals.calendar_cell_repeats, day.repeats.size, day.repeats.size)
    if (day.reminders > 0) parts += pluralStringResource(R.plurals.calendar_cell_reminders, day.reminders, day.reminders)
    return parts.joinToString(", ")
}

/**
 * One line of what a day holds, with its [project]'s chip when it is a project item; a planned task
 * can be dragged onto another day of the grid.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun DayRow(
    task: TaskItem,
    label: Int,
    onOpenTask: (String) -> Unit,
    project: ProjectItem?,
    onOpenProject: ((String) -> Unit)?,
    draggable: Boolean = false,
    onDone: ((TaskItem, Boolean) -> Unit)? = null,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .background(AppTheme.colors.surface, AppTheme.shapes.row)
            .clickable { onOpenTask(task.id) }
            .then(
                if (!draggable) {
                    Modifier
                } else {
                    Modifier.dragAndDropSource(
                        transferData = {
                            DragAndDropTransferData(ClipData.newPlainText(task.title, task.id))
                        },
                    )
                },
            )
            .padding(start = if (onDone == null) 12.dp else 0.dp, end = 12.dp, top = 10.dp, bottom = 10.dp),
    ) {
        // A planned task or a deadline is ticked off here on the open day; a repeat has no row to tick yet.
        if (onDone != null && task.state != TaskState.DROPPED) {
            GoalMakerCheckbox(checked = task.state == TaskState.DONE, onCheckedChange = { done -> onDone(task, done) })
        }
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
            project?.let { item -> ProjectChip(item, task.itemType, onOpenProject?.let { open -> { open(item.id) } }, Modifier.padding(top = 4.dp)) }
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

// A day cell's width to its height, unless large text needs it taller.
private const val CELL_RATIO = 0.9f
