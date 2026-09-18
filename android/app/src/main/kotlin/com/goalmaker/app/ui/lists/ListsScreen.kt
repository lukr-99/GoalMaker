package com.goalmaker.app.ui.lists

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
import androidx.compose.foundation.text.input.clearText
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.EditCalendar
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Inbox
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Today
import androidx.compose.material.icons.outlined.WbTwilight
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
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.goalmaker.app.R
import com.goalmaker.app.application.planning.AreaItem
import com.goalmaker.app.application.planning.PlanRules
import com.goalmaker.app.application.planning.PlanningLists
import com.goalmaker.app.application.planning.TaskItem
import com.goalmaker.app.ui.components.rememberTickSound
import com.goalmaker.app.ui.composer.ComposerBar
import com.goalmaker.app.ui.composer.composerChips
import com.goalmaker.app.ui.composer.removeParts
import com.goalmaker.app.ui.theme.AppTheme
import java.time.format.DateTimeFormatter

/**
 * The planner's home: Today, Tomorrow and the Inbox behind a bottom navigation (docs/lists.md), with
 * the composer above it. The list on screen supplies the day for what's typed (docs/composer.md).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ListsScreen(viewModel: ListsViewModel, onOpenPlan: () -> Unit, onOpenSettings: () -> Unit) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableStateOf(ListTab.TODAY) }
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val composer = rememberTextFieldState()
    val snackbars = remember { SnackbarHostState() }
    val resources = LocalResources.current
    val tick = rememberTickSound()

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

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        snackbarHost = { SnackbarHost(snackbars) },
        topBar = {
            LargeFlexibleTopAppBar(
                title = { Text(AppTheme.headline(stringResource(tab.title()))) },
                subtitle = { state.lists?.let { Text(subtitle(tab, it)) } },
                actions = {
                    SyncIndicator(state.sync, onSyncNow = viewModel::refresh)
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
                    chips = composerChips(line, draft, viewModel.today(), state.areas, state.tagNames),
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
                ListContent(tab, lists, state.areas, viewModel, tick)
            }
        }
    }
}

@Composable
private fun ListContent(tab: ListTab, lists: PlanningLists, areas: List<AreaItem>, viewModel: ListsViewModel, tick: () -> Unit) {
    var overdueOpen by rememberSaveable { mutableStateOf(false) }
    val areaById = areas.associateBy(AreaItem::id)
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
                    tick = tick,
                    modifier = Modifier.animateItem(),
                )
            }
        }
        when (tab) {
            ListTab.TODAY -> {
                val sections = lists.todaySections
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

/** A list's section label; with [onToggle] it folds and shows whether it's open. */
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
            color = AppTheme.colors.textMuted,
            modifier = Modifier.weight(1f),
        )
        if (expanded != null) {
            Icon(if (expanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore, contentDescription = null, tint = AppTheme.colors.textMuted)
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
    val longDate = DateTimeFormatter.ofPattern("EEEE d MMMM", locale)
    return when (tab) {
        ListTab.TODAY -> {
            val date = longDate.format(lists.today)
            if (lists.summary.total == 0) date else pluralStringResource(R.plurals.lists_today_summary, lists.summary.total, date, lists.summary.done, lists.summary.total)
        }
        ListTab.TOMORROW -> longDate.format(lists.today.plusDays(1))
        ListTab.INBOX -> pluralStringResource(R.plurals.lists_inbox_count, lists.inbox.size, lists.inbox.size)
    }
}

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
