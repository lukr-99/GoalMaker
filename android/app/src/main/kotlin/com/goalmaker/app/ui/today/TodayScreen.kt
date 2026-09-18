package com.goalmaker.app.ui.today

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.input.clearText
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.goalmaker.app.R
import com.goalmaker.app.application.planning.TaskItem
import com.goalmaker.app.ui.composer.ComposerBar
import com.goalmaker.app.ui.composer.composerChips
import com.goalmaker.app.ui.composer.removeParts
import com.goalmaker.app.ui.theme.AppTheme

/** Today: the open tasks, synced; the composer adds more with a live preview. M2-06 brings days and sections. */
@Composable
fun TodayScreen(viewModel: TodayViewModel, onOpenSettings: () -> Unit) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val composer = rememberTextFieldState()
    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeFlexibleTopAppBar(
                title = { Text(AppTheme.headline(stringResource(R.string.today_title))) },
                subtitle = { Text(syncStatusLabel(state.sync)) },
                actions = {
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Outlined.Settings, contentDescription = stringResource(R.string.today_settings))
                    }
                },
                scrollBehavior = scrollBehavior,
            )
        },
        bottomBar = {
            val line = composer.text.toString()
            val draft = remember(line) { viewModel.preview(line) }
            ComposerBar(
                state = composer,
                chips = composerChips(line, draft, viewModel.today(), state.areas, state.tagNames),
                canSend = draft.title.isNotBlank() && draft.command == null,
                onSubmit = { if (viewModel.submit(draft)) composer.clearText() },
                onRemove = { chip -> composer.setTextAndPlaceCursorAtEnd(removeParts(line, chip.spans)) },
            )
        },
    ) { padding ->
        PullToRefreshBox(
            isRefreshing = state.refreshing,
            onRefresh = viewModel::refresh,
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            if (!state.loaded) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    LoadingIndicator(Modifier.size(64.dp))
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    if (state.tasks.isEmpty()) {
                        item(key = "empty") {
                            Text(
                                text = stringResource(R.string.today_empty),
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(8.dp).animateItem(),
                            )
                        }
                    }
                    items(state.tasks, key = TaskItem::id) { task ->
                        TaskRow(
                            task = task,
                            onDone = { viewModel.setDone(task.id, it) },
                            onDelete = { viewModel.delete(task.id) },
                            modifier = Modifier.animateItem(),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TaskRow(task: TaskItem, onDone: (Boolean) -> Unit, onDelete: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        color = AppTheme.colors.surface,
        shape = AppTheme.shapes.row,
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 4.dp, end = 4.dp)) {
            Checkbox(
                checked = false,
                onCheckedChange = onDone,
                colors = CheckboxDefaults.colors(
                    checkedColor = AppTheme.colors.accent,
                    checkmarkColor = AppTheme.colors.onAccent,
                    uncheckedColor = AppTheme.colors.outline,
                ),
            )
            Text(
                text = task.title,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier
                    .weight(1f)
                    .padding(vertical = 12.dp),
            )
            IconButton(onClick = onDelete) {
                Icon(Icons.Outlined.Delete, contentDescription = stringResource(R.string.today_delete, task.title))
            }
        }
    }
}
