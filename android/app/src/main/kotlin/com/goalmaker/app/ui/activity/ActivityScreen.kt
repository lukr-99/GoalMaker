package com.goalmaker.app.ui.activity

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MediumFlexibleTopAppBar
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.goalmaker.app.R
import com.goalmaker.app.application.activity.UndoOutcome
import com.goalmaker.app.ui.theme.AppTheme
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Settings, Activity (docs/activity.md): recent changes by the owner and by Claude, newest first,
 * with Undo on each row's latest change.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ActivityScreen(viewModel: ActivityViewModel, onBack: () -> Unit) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val times = DateTimeFormatter.ofPattern("EEE d MMM, HH:mm", LocalConfiguration.current.locales[0])
    val snackbar = remember { SnackbarHostState() }
    val outcome = state.undone
    val message = outcome?.let {
        stringResource(
            when (it) {
                UndoOutcome.UNDONE -> R.string.activity_undo_done
                UndoOutcome.CHANGED_SINCE -> R.string.activity_undo_changed
                UndoOutcome.ALREADY_UNDONE -> R.string.activity_undo_already
                UndoOutcome.NOT_POSSIBLE -> R.string.activity_undo_impossible
            },
        )
    }
    LaunchedEffect(message) {
        if (message != null) {
            snackbar.showSnackbar(message)
            viewModel.messageShown()
        }
    }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            MediumFlexibleTopAppBar(
                title = { Text(AppTheme.headline(stringResource(R.string.activity_title))) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.settings_back))
                    }
                },
                actions = {
                    IconButton(onClick = viewModel::refresh, enabled = !state.busy) {
                        Icon(Icons.Outlined.Refresh, contentDescription = stringResource(R.string.activity_refresh))
                    }
                },
                scrollBehavior = scrollBehavior,
            )
        },
    ) { padding ->
        LazyColumn(
            contentPadding = PaddingValues(horizontal = AppTheme.density.pagePadding.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(AppTheme.density.rowGap.dp),
            modifier = Modifier.fillMaxSize().padding(padding),
        ) {
            item { Text(stringResource(R.string.activity_intro), color = AppTheme.colors.textMuted) }
            when {
                !state.loaded -> item { Text(stringResource(R.string.connector_loading), color = AppTheme.colors.textMuted) }
                state.unavailable -> item {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(stringResource(R.string.activity_offline), style = MaterialTheme.typography.bodyLarge)
                        OutlinedButton(onClick = viewModel::refresh, enabled = !state.busy) {
                            Text(stringResource(R.string.connector_retry))
                        }
                    }
                }
                state.rows.isEmpty() -> item { Text(stringResource(R.string.activity_empty), style = MaterialTheme.typography.bodyLarge) }
            }
            if (!state.unavailable) {
                items(state.rows, key = { it.entry.id }) { row ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = AppTheme.density.rowMinHeight.dp)
                            .background(AppTheme.colors.surface, AppTheme.shapes.row)
                            .padding(start = 16.dp, end = 4.dp),
                    ) {
                        Column(Modifier.weight(1f).padding(vertical = 8.dp)) {
                            Text(activitySentence(row), style = MaterialTheme.typography.bodyLarge)
                            Text(
                                row.entry.createdAt.atZone(ZoneId.systemDefault()).format(times) +
                                    if (row.entry.undoneAt != null) " · " + stringResource(R.string.activity_undone) else "",
                                style = MaterialTheme.typography.bodySmall,
                                color = AppTheme.colors.textMuted,
                            )
                        }
                        if (row.undoable) {
                            TextButton(onClick = { viewModel.undo(row) }, enabled = !state.busy) {
                                Text(stringResource(R.string.activity_undo))
                            }
                        }
                    }
                }
            }
        }
    }
}
