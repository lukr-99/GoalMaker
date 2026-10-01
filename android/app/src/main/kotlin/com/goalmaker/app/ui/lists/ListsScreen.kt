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
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
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
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material3.Icon
import androidx.compose.material3.MediumFlexibleTopAppBar
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
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
import com.goalmaker.app.application.planning.WantRules
import com.goalmaker.app.application.planning.PlanningLists
import com.goalmaker.app.application.planning.ReminderItem
import com.goalmaker.app.application.planning.TaskItem
import com.goalmaker.app.domain.settings.ComposerMode
import com.goalmaker.app.ui.chat.ChatThread
import com.goalmaker.app.ui.chat.ChatUnavailableNote
import com.goalmaker.app.ui.chat.ChatViewModel
import com.goalmaker.app.ui.chat.ComposerModeSwitch
import com.goalmaker.app.ui.components.AppSnackbarHost
import com.goalmaker.app.ui.components.ConfettiBurst
import com.goalmaker.app.ui.components.GoalMakerLogo
import com.goalmaker.app.ui.components.ProgressRing
import com.goalmaker.app.ui.components.rememberTickSound
import com.goalmaker.app.ui.components.ScreenTitle
import com.goalmaker.app.ui.nav.NavTransitions
import com.goalmaker.app.ui.composer.ComposerBar
import com.goalmaker.app.ui.composer.composerChips
import com.goalmaker.app.ui.composer.removeParts
import com.goalmaker.app.ui.goals.GoalSummaryRow
import com.goalmaker.app.ui.habits.AmountDialog
import com.goalmaker.app.ui.habits.HabitSheet
import com.goalmaker.app.ui.habits.HabitRow
import com.goalmaker.app.ui.nav.PlaceNavigationIcon
import com.goalmaker.app.ui.theme.AppTheme
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * The planner's home: Today, Tomorrow and the Inbox behind the bottom navigation (docs/lists.md),
 * with the composer above it. The list on screen supplies the day for what's typed
 * (docs/composer.md). The bar itself is shared with Projects and the Calendar, so [tab] and the
 * choosing live above this screen, in [com.goalmaker.app.ui.nav.MainScreen]. The composer's switch
 * turns it into the quick chat ([chat]), whose short thread shows above it.
 */
@Composable
fun ListsScreen(
    viewModel: ListsViewModel,
    chat: ChatViewModel,
    onOpenPlan: () -> Unit,
    onOpenTask: (String) -> Unit,
    onOpenGoals: () -> Unit,
    onOpenHabits: () -> Unit,
    tab: ListTab,
    actions: @Composable () -> Unit,
    onBack: (() -> Unit)? = null,
    onOpenWant: (String) -> Unit = {},
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val chatState by chat.uiState.collectAsStateWithLifecycle()
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val composer = rememberTextFieldState()
    val snackbars = remember { SnackbarHostState() }
    val resources = LocalResources.current
    val tick = rememberTickSound()
    var remindFor by remember { mutableStateOf<TaskItem?>(null) }
    var taskReminders by remember { mutableStateOf(emptyList<ReminderItem>()) }
    var logging by remember { mutableStateOf<HabitItem?>(null) }
    var habitMenu by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current

    // A habit card's button: undo a skip, or check in; an amount asks for its value first.
    fun checkInHabit(row: HabitRow) {
        haptics.performHapticFeedback(HapticFeedbackType.ToggleOn)
        if (row.skipped) {
            viewModel.skipHabit(row.habit.id, false)
        } else {
            scope.launch { if (!viewModel.tapHabit(row.habit.id)) logging = row.habit }
        }
    }

    // A list tab arrives the way Projects and the Calendar do, since the bar treats all five alike.
    val motion = AppTheme.motion
    val reduceMotion = AppTheme.reduceMotion
    val transitions = remember(motion, reduceMotion) { NavTransitions(motion, reduceMotion) }

    // Confetti when a habit's streak reaches a milestone while Today is open (design spec).
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
    // A habit's menu, from its card's menu button or a long press: skipping lives here.
    habitMenu?.let { id ->
        state.habits.firstOrNull { it.habit.id == id }?.let { row ->
            HabitSheet(
                row = row,
                onDismiss = { habitMenu = null },
                onCheckIn = { checkInHabit(row) },
                onLog = { logging = row.habit },
                onSkip = { skipped -> viewModel.skipHabit(id, skipped) },
                onClear = { viewModel.clearHabit(id) },
                onPause = { viewModel.pauseHabit(id) },
                onOpenHabits = onOpenHabits,
            )
        }
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
            snackbarHost = { AppSnackbarHost(snackbars) },
            topBar = {
                MediumFlexibleTopAppBar(
                    title = { ScreenTitle(stringResource(tab.title())) },
                    subtitle = {
                        state.lists?.let {
                            Text(subtitle(tab, it), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    },
                    navigationIcon = { PlaceNavigationIcon(onBack) { DayMark(tab, state.lists) } },
                    actions = { actions() },
                    scrollBehavior = scrollBehavior,
                )
            },
            bottomBar = {
                // Under MainScreen's bar the navigation bar is already taken, so this adds nothing there.
                Column(Modifier.navigationBarsPadding().imePadding()) {
                    val line = composer.text.toString()
                    // Picked chat but it can't run: say why, and the composer quick-adds meanwhile.
                    if (chatState.chatBlocked) ChatUnavailableNote(chatState.availability)
                    if (chatState.chosen == ComposerMode.CHAT && (chatState.lines.isNotEmpty() || chatState.thinking)) {
                        ChatThread(chatState, onClear = chat::clear, onRetry = chat::retry)
                    }
                    // One bar for both modes, so switching keeps the line and the keyboard.
                    val chatting = chatState.chatting
                    val draft = remember(line) { viewModel.preview(line) }
                    ComposerBar(
                        state = composer,
                        chips = if (chatting) emptyList() else composerChips(line, draft, viewModel.today(), state.areas, state.tagNames, state.projects),
                        canSend = if (chatting) {
                            line.isNotBlank() && !chatState.thinking
                        } else {
                            (draft.title.isNotBlank() && draft.command == null) ||
                                draft.command?.name == PlanRules.COMMAND || draft.command?.name == WantRules.COMMAND
                        },
                        onSubmit = {
                            if (chatting) {
                                if (chat.send(line)) composer.clearText()
                            } else if (draft.command?.name == PlanRules.COMMAND) {
                                composer.clearText()
                                onOpenPlan()
                            } else if (draft.command?.name == WantRules.COMMAND) {
                                composer.clearText()
                                onOpenWant(draft.command.argument)
                            } else if (viewModel.submit(draft, tab)) {
                                composer.clearText()
                            }
                        },
                        onRemove = { chip -> composer.setTextAndPlaceCursorAtEnd(removeParts(line, chip.spans)) },
                        placeholder = stringResource(if (chatting) R.string.chat_placeholder else R.string.today_composer_placeholder),
                        sendLabel = stringResource(if (chatting) R.string.chat_send else R.string.today_add),
                        leading = { ComposerModeSwitch(chatState, chat::choose) },
                    )
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
                        AnimatedContent(
                            targetState = tab,
                            transitionSpec = { transitions.switch() },
                            label = "list tab",
                        ) { shown ->
                            ListContent(
                                tab = shown,
                                lists = lists,
                                state = state,
                                viewModel = viewModel,
                                tick = tick,
                                onOpenTask = onOpenTask,
                                onOpenGoals = onOpenGoals,
                                onOpenHabits = onOpenHabits,
                                onCheckInHabit = ::checkInHabit,
                                onHabitMenu = { habitMenu = it.habit.id },
                                onRemind = { remindFor = it },
                            )
                        }
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
    onCheckInHabit: (HabitRow) -> Unit,
    onHabitMenu: (HabitRow) -> Unit,
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
                // Tasks and habits each have a half of Today, behind the switch (the habits prototype, option C).
                // Without a habit on Today there is nothing to switch to.
                if (state.habits.isNotEmpty() || state.segment == TodaySegment.HABITS) item(key = "switch") {
                    TodaySwitch(
                        shown = state.segment,
                        openTasks = sections.priorities.size + sections.scheduled.size + sections.more.size,
                        habitsLeft = state.habitsLeft,
                        habitsDone = state.habitsAllDone,
                        onShow = viewModel::showSegment,
                        modifier = Modifier.padding(top = 4.dp, bottom = 4.dp),
                    )
                }
                if (state.segment == TodaySegment.HABITS) {
                    habits(state, lists.today, viewModel, onOpenHabits, onCheckInHabit, onHabitMenu)
                    return@LazyColumn
                }
                val labelled = listOf(sections.priorities, sections.scheduled, sections.more).count { it.isNotEmpty() } > 1 ||
                    sections.priorities.isNotEmpty() || sections.scheduled.isNotEmpty()
                if (sections.priorities.isNotEmpty()) {
                    item(key = "h-priorities") { SectionHeader(stringResource(R.string.lists_priorities)) }
                    rows(sections.priorities)
                }
                if (sections.scheduled.isNotEmpty()) {
                    item(key = "h-scheduled") { SectionHeader(stringResource(R.string.lists_scheduled)) }
                    rows(sections.scheduled)
                }
                if (sections.more.isNotEmpty()) {
                    if (labelled) item(key = "h-more") { SectionHeader(stringResource(R.string.lists_more)) }
                    rows(sections.more)
                }
                if (sections.priorities.isEmpty() && sections.scheduled.isEmpty() && sections.more.isEmpty()) {
                    item(key = "empty") { Empty(stringResource(R.string.lists_today_empty)) }
                }
                // What is left of the habits, one tap from their half of Today.
                if (state.habitsLeft > 0) {
                    item(key = "nudge") {
                        HabitsNudge(
                            left = state.habits.filter(HabitRow::left),
                            onClick = { viewModel.showSegment(TodaySegment.HABITS) },
                            modifier = Modifier.padding(top = 12.dp).animateItem(),
                        )
                    }
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
