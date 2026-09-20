package com.goalmaker.app.ui.lists

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.input.clearText
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DonutLarge
import androidx.compose.material.icons.outlined.EditCalendar
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material.icons.outlined.Inbox
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Today
import androidx.compose.material.icons.outlined.WbTwilight
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.goalmaker.app.R
import com.goalmaker.app.application.planning.AreaItem
import com.goalmaker.app.application.planning.HabitItem
import com.goalmaker.app.application.planning.PlanRules
import com.goalmaker.app.application.planning.PlanningLists
import com.goalmaker.app.application.planning.ReminderItem
import com.goalmaker.app.application.planning.TaskItem
import com.goalmaker.app.ui.components.ConfettiBurst
import com.goalmaker.app.ui.components.GoalMakerLogo
import com.goalmaker.app.ui.components.ProgressRing
import com.goalmaker.app.ui.components.rememberTickSound
import com.goalmaker.app.ui.components.ScreenTitle
import com.goalmaker.app.ui.composer.ComposerBar
import com.goalmaker.app.ui.composer.composerChips
import com.goalmaker.app.ui.composer.removeParts
import com.goalmaker.app.ui.goals.GoalSummaryRow
import com.goalmaker.app.ui.habits.AmountDialog
import com.goalmaker.app.ui.habits.HabitRingsRow
import com.goalmaker.app.ui.habits.HabitRow
import com.goalmaker.app.ui.theme.AppTheme
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * The planner's home: Today, Tomorrow and the Inbox behind a bottom navigation (docs/lists.md), with
 * the composer above it. The list on screen supplies the day for what's typed (docs/composer.md).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ListsScreen(
    viewModel: ListsViewModel,
    onOpenPlan: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenTask: (String) -> Unit,
    onOpenArchive: () -> Unit,
    onOpenGoals: () -> Unit,
    onOpenHabits: () -> Unit,
    onOpenReviews: () -> Unit,
    onOpenStats: () -> Unit,
    onOpenProjects: () -> Unit,
    onOpenCalendar: () -> Unit,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableStateOf(ListTab.TODAY) }
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val composer = rememberTextFieldState()
    val snackbars = remember { SnackbarHostState() }
    val resources = LocalResources.current
    val tick = rememberTickSound()
    var remindFor by remember { mutableStateOf<TaskItem?>(null) }
    var taskReminders by remember { mutableStateOf(emptyList<ReminderItem>()) }
    var logging by remember { mutableStateOf<HabitItem?>(null) }
    val scope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current

    // A tap on a habit's ring checks in; an amount asks for its value first.
    fun tapHabit(row: HabitRow) {
        haptics.performHapticFeedback(HapticFeedbackType.ToggleOn)
        scope.launch { if (!viewModel.tapHabit(row.habit.id)) logging = row.habit }
    }

    // Confetti when a habit's streak reaches a milestone while Today is open (design spec).
    val reduceMotion = AppTheme.reduceMotion
    var seenMilestones by remember { mutableStateOf<Set<String>?>(null) }
    var bursts by remember { mutableIntStateOf(0) }
    LaunchedEffect(state.lists != null, state.habitMilestones) {
        if (state.lists == null) return@LaunchedEffect
        val before = seenMilestones
        seenMilestones = state.habitMilestones
        if (before != null && !reduceMotion && !before.containsAll(state.habitMilestones)) bursts++
    }
    logging?.let { habit ->
        AmountDialog(habit, onLog = { amount -> viewModel.checkIn(habit.id, amount) }, onDismiss = { logging = null })
    }

    // The sheet reads the task's reminders when it opens, and again after every change to them.
    LaunchedEffect(remindFor, state.reminded) {
        taskReminders = remindFor?.let { viewModel.remindersOf(it.id) }.orEmpty()
    }

    remindFor?.let { task ->
        ReminderSheet(
            task = task,
            reminders = taskReminders,
            onRemindWhenDue = { viewModel.remindBefore(task.id, 0) },
            onRemindBefore = { minutes -> viewModel.remindBefore(task.id, minutes) },
            onRemindInAnHour = { viewModel.remindAt(task.id, LocalDateTime.now().plusHours(1)) },
            onRemindTomorrowMorning = { viewModel.remindAt(task.id, viewModel.tomorrowMorning()) },
            onRemove = viewModel::removeReminder,
            onDismiss = { remindFor = null },
        )
    }

    LaunchedEffect(viewModel) {
        viewModel.undo.collect { event ->
            val message = resources.getString(
                if (event.kind == UndoEvent.Kind.DONE) R.string.lists_done_message else R.string.lists_deleted_message,
                event.title,
            )
            val result = snackbars.showSnackbar(message, actionLabel = resources.getString(R.string.lists_undo), duration = SnackbarDuration.Short)
            if (result == SnackbarResult.ActionPerformed) event.undo()
        }
    }

    Box(Modifier.fillMaxSize()) {
        Scaffold(
            modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
            snackbarHost = { SnackbarHost(snackbars) },
            topBar = {
                LargeFlexibleTopAppBar(
                    title = { ScreenTitle(stringResource(tab.title())) },
                    subtitle = {
                        state.lists?.let {
                            Text(subtitle(tab, it), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    },
                    navigationIcon = { DayMark(tab, state.lists) },
                    actions = {
                        SyncIndicator(state.sync, onSyncNow = viewModel::refresh)
                        IconButton(onClick = onOpenHabits) {
                            Icon(Icons.Outlined.DonutLarge, contentDescription = stringResource(R.string.habits_title))
                        }
                        IconButton(onClick = onOpenGoals) {
                            Icon(Icons.Outlined.Flag, contentDescription = stringResource(R.string.goals_title))
                        }
                        MoreMenu(
                            onOpenArchive = onOpenArchive,
                            onOpenReviews = onOpenReviews,
                            onOpenStats = onOpenStats,
                            onOpenProjects = onOpenProjects,
                            onOpenCalendar = onOpenCalendar,
                        )
                        IconButton(onClick = onOpenPlan) {
                            Icon(Icons.Outlined.EditCalendar, contentDescription = stringResource(R.string.plan_title))
                        }
                        IconButton(onClick = onOpenSettings) {
                            Icon(Icons.Outlined.Settings, contentDescription = stringResource(R.string.today_settings))
                        }
                    },
                    scrollBehavior = scrollBehavior,
                )
            },
            bottomBar = {
                Column(Modifier.imePadding()) {
                    val line = composer.text.toString()
                    val draft = remember(line) { viewModel.preview(line) }
                    ComposerBar(
                        state = composer,
                        chips = composerChips(line, draft, viewModel.today(), state.areas, state.tagNames, state.projects),
                        canSend = (draft.title.isNotBlank() && draft.command == null) || draft.command?.name == PlanRules.COMMAND,
                        onSubmit = {
                            if (draft.command?.name == PlanRules.COMMAND) {
                                composer.clearText()
                                onOpenPlan()
                            } else if (viewModel.submit(draft, tab)) {
                                composer.clearText()
                            }
                        },
                        onRemove = { chip -> composer.setTextAndPlaceCursorAtEnd(removeParts(line, chip.spans)) },
                    )
                    if (!WindowInsets.isImeVisible) {
                        NavigationBar {
                            ListTab.entries.forEach { entry ->
                                NavigationBarItem(
                                    selected = tab == entry,
                                    onClick = { tab = entry },
                                    icon = { Icon(entry.icon(), contentDescription = null) },
                                    label = { Text(stringResource(entry.title())) },
                                )
                            }
                        }
                    }
                }
            },
        ) { padding ->
            PullToRefreshBox(
                isRefreshing = state.refreshing,
                onRefresh = viewModel::refresh,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
            ) {
                val lists = state.lists
                if (lists == null) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        LoadingIndicator(Modifier.size(64.dp))
                    }
                } else {
                    Column {
                        ListFilterRow(
                            filter = state.filter,
                            areas = state.areas.filterNot { it.archived },
                            tags = state.tags,
                            onArea = viewModel::filterByArea,
                            onTag = viewModel::filterByTag,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                        ListContent(tab, lists, state, viewModel, tick, onOpenTask, onOpenGoals, onOpenHabits, ::tapHabit) { remindFor = it }
                    }
                }
            }
        }
        if (bursts > 0) {
            key(bursts) { ConfettiBurst(onFinished = { bursts = 0 }) }
        }
    }
}

@Composable
private fun ListContent(
    tab: ListTab,
    lists: PlanningLists,
    state: ListsUiState,
    viewModel: ListsViewModel,
    tick: () -> Unit,
    onOpenTask: (String) -> Unit,
    onOpenGoals: () -> Unit,
    onOpenHabits: () -> Unit,
    onTapHabit: (HabitRow) -> Unit,
    onRemind: (TaskItem) -> Unit,
) {
    var overdueOpen by rememberSaveable { mutableStateOf(false) }
    var goalsOpen by rememberSaveable { mutableStateOf(false) }
    val areaById = state.areas.associateBy(AreaItem::id)
    LazyColumn(
        contentPadding = PaddingValues(horizontal = AppTheme.density.pagePadding.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(AppTheme.density.rowGap.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        fun LazyListScope.rows(tasks: List<TaskItem>, showDay: Boolean = false) {
            items(tasks, key = TaskItem::id) { task ->
                TaskRow(
                    task = task,
                    area = task.areaId?.let(areaById::get),
                    showDay = showDay,
                    onComplete = { viewModel.complete(task) },
                    onDelete = { viewModel.delete(task) },
                    onRemind = { onRemind(task) },
                    onOpen = { onOpenTask(task.id) },
                    reminded = task.id in state.reminded,
                    tick = tick,
                    modifier = Modifier.animateItem(),
                )
            }
        }
        when (tab) {
            ListTab.TODAY -> {
                val sections = lists.todaySections
                val labelled = listOf(sections.priorities, sections.scheduled, sections.more).count { it.isNotEmpty() } > 1 ||
                    sections.priorities.isNotEmpty() || sections.scheduled.isNotEmpty() || state.habits.isNotEmpty()
                if (sections.priorities.isNotEmpty()) {
                    item(key = "h-priorities") { SectionHeader(stringResource(R.string.lists_priorities)) }
                    rows(sections.priorities)
                }
                if (sections.scheduled.isNotEmpty()) {
                    item(key = "h-scheduled") { SectionHeader(stringResource(R.string.lists_scheduled)) }
                    rows(sections.scheduled)
                }
                // Today's habits as a row of rings, between the timed tasks and the rest (design spec, Today).
                if (state.habits.isNotEmpty()) {
                    item(key = "h-habits") {
                        val left = state.habits.count { !it.done }
                        SectionHeader(
                            text = if (left == 0) stringResource(R.string.habits_today_done) else pluralStringResource(R.plurals.habits_today_left, left, left),
                            onToggle = onOpenHabits,
                        )
                    }
                    item(key = "habits") { HabitRingsRow(state.habits, onTap = onTapHabit, onOpen = onOpenHabits, modifier = Modifier.animateItem()) }
                }
                if (sections.more.isNotEmpty()) {
                    if (labelled) item(key = "h-more") { SectionHeader(stringResource(R.string.lists_more)) }
                    rows(sections.more)
                }
                if (sections.priorities.isEmpty() && sections.scheduled.isEmpty() && sections.more.isEmpty()) {
                    item(key = "empty") { Empty(stringResource(R.string.lists_today_empty)) }
                }
                if (sections.overdue.isNotEmpty()) {
                    item(key = "h-overdue") {
                        SectionHeader(
                            text = pluralStringResource(R.plurals.lists_overdue, sections.overdue.size, sections.overdue.size),
                            expanded = overdueOpen,
                            onToggle = { overdueOpen = !overdueOpen },
                        )
                    }
                    if (overdueOpen) rows(sections.overdue, showDay = true)
                }
                // This week's goals, folded like the overdue ones (design spec, Today).
                if (state.weekGoals.isNotEmpty()) {
                    val goals = state.weekGoals
                    item(key = "h-goals") {
                        SectionHeader(
                            text = pluralStringResource(R.plurals.goals_week_count, goals.size, goals.count { it.progress.hit }, goals.size),
                            expanded = goalsOpen,
                            onToggle = { goalsOpen = !goalsOpen },
                        )
                    }
                    if (goalsOpen) {
                        items(goals, key = { "goal-" + it.goal.id }) { row -> GoalSummaryRow(row, onClick = onOpenGoals, modifier = Modifier.animateItem()) }
                        item(key = "all-goals") { TextButton(onClick = onOpenGoals) { Text(stringResource(R.string.goals_all)) } }
                    }
                }
            }
            ListTab.TOMORROW -> {
                if (lists.tomorrow.isEmpty()) item(key = "empty") { Empty(stringResource(R.string.lists_tomorrow_empty)) }
                rows(lists.tomorrow)
            }
            ListTab.INBOX -> {
                if (lists.inbox.isEmpty()) item(key = "empty") { Empty(stringResource(R.string.lists_inbox_empty)) }
                rows(lists.inbox)
            }
        }
    }
}

/** What doesn't fit the top bar: the projects, the reviews, the stats and the archive. */
@Composable
private fun MoreMenu(
    onOpenArchive: () -> Unit,
    onOpenReviews: () -> Unit,
    onOpenStats: () -> Unit,
    onOpenProjects: () -> Unit,
    onOpenCalendar: () -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }) {
            Icon(Icons.Outlined.MoreVert, contentDescription = stringResource(R.string.lists_more_menu))
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.calendar_title)) },
                onClick = {
                    open = false
                    onOpenCalendar()
                },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.projects_title)) },
                onClick = {
                    open = false
                    onOpenProjects()
                },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.reviews_title)) },
                onClick = {
                    open = false
                    onOpenReviews()
                },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.stats_title)) },
                onClick = {
                    open = false
                    onOpenStats()
                },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.archive_title)) },
                onClick = {
                    open = false
                    onOpenArchive()
                },
            )
        }
    }
}

/**
  * The mark in the top bar: the GoalMaker logo, which turns into today's ring whenever a task is
  * ticked off or the mark is tapped, holds for a moment and then draws itself again (the owner asked).
  * Tomorrow and the Inbox keep the logo, since the ring is today's.
  */
@Composable
private fun DayMark(tab: ListTab, lists: PlanningLists?) {
    val motion = AppTheme.motion
    val reduced = AppTheme.reduceMotion
    val done = lists?.summary?.done ?: 0
    val total = lists?.summary?.total ?: 0
    val ringable = tab == ListTab.TODAY && total > 0
    var pulse by remember { mutableIntStateOf(0) }
    var ring by remember { mutableStateOf(false) }
    // The first count is what the day already stood at, not something just finished.
    var counted by remember { mutableIntStateOf(done) }
    LaunchedEffect(done, ringable) {
        if (done != counted) {
            counted = done
            if (ringable) pulse++
        }
    }
    LaunchedEffect(pulse) {
        if (pulse == 0) return@LaunchedEffect
        ring = true
        delay(RING_MOMENT)
        ring = false
    }

    val label = if (ringable) stringResource(R.string.lists_today_progress, done, total) else stringResource(R.string.app_name)
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .padding(start = 12.dp)
            .size(40.dp)
            .clip(CircleShape)
            .clickable(enabled = ringable) { pulse++ }
            .semantics { contentDescription = label },
    ) {
        AnimatedContent(
            targetState = ring && ringable,
            transitionSpec = {
                val enter = fadeIn(tween(motion.quick)) + if (reduced) EnterTransition.None else scaleIn(initialScale = 0.8f)
                val exit = fadeOut(tween(motion.quick)) + if (reduced) ExitTransition.None else scaleOut(targetScale = 0.8f)
                enter togetherWith exit
            },
            label = "day mark",
        ) { showRing ->
            if (showRing) {
                ProgressRing(fraction = done.toFloat() / total, size = 34.dp, stroke = 3.dp) {
                    Text(
                        done.toString(),
                        style = MaterialTheme.typography.labelMedium,
                        color = if (done == total) AppTheme.colors.accent else AppTheme.colors.text,
                    )
                }
            } else {
                GoalMakerLogo(size = 32.dp, intro = true)
            }
        }
    }
}

/** A list's section label in the theme's accent; with [onToggle] it folds and shows whether it's open. */
@Composable
internal fun SectionHeader(text: String, expanded: Boolean? = null, onToggle: (() -> Unit)? = null) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .let { if (onToggle != null) it.clickable(onClick = onToggle) else it }
            .padding(top = 16.dp, bottom = 4.dp, start = 4.dp)
            .semantics { heading() },
    ) {
        Text(
            text = AppTheme.headline(text).uppercase(LocalConfiguration.current.locales[0]),
            style = MaterialTheme.typography.labelMedium,
            color = AppTheme.colors.accent,
            modifier = Modifier.weight(1f),
        )
        if (expanded != null) {
            Icon(if (expanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore, contentDescription = null, tint = AppTheme.colors.accent)
        }
    }
}

@Composable
private fun Empty(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyLarge,
        color = AppTheme.colors.textMuted,
        modifier = Modifier.padding(8.dp),
    )
}

@Composable
private fun subtitle(tab: ListTab, lists: PlanningLists): String {
    val locale = LocalConfiguration.current.locales[0]
    val date = DateTimeFormatter.ofPattern("EEE d MMM", locale)
    return when (tab) {
        ListTab.TODAY -> date.format(lists.today)
        ListTab.TOMORROW -> date.format(lists.today.plusDays(1))
        ListTab.INBOX -> pluralStringResource(R.plurals.lists_inbox_count, lists.inbox.size, lists.inbox.size)
    }
}

// How long the ring holds before the mark goes back to the logo.
private const val RING_MOMENT = 2600L

private fun ListTab.title() = when (this) {
    ListTab.TODAY -> R.string.lists_today
    ListTab.TOMORROW -> R.string.lists_tomorrow
    ListTab.INBOX -> R.string.lists_inbox
}

private fun ListTab.icon() = when (this) {
    ListTab.TODAY -> Icons.Outlined.Today
    ListTab.TOMORROW -> Icons.Outlined.WbTwilight
    ListTab.INBOX -> Icons.Outlined.Inbox
}
