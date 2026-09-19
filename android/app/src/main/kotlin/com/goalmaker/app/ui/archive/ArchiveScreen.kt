package com.goalmaker.app.ui.archive

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MediumFlexibleTopAppBar
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import com.goalmaker.app.ui.components.ScreenTitle
import com.goalmaker.app.R
import com.goalmaker.app.domain.sync.SyncRules
import com.goalmaker.app.ui.theme.AppTheme
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** The archive of done tasks (docs/archive.md): search, open one, or reopen it. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArchiveScreen(viewModel: ArchiveViewModel, onBack: () -> Unit, onOpenTask: (String) -> Unit) {
    val query by viewModel.query.collectAsStateWithLifecycle()
    val results by viewModel.results.collectAsStateWithLifecycle()
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val dates = DateTimeFormatter.ofPattern("EEE d MMM", LocalConfiguration.current.locales[0])

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            MediumFlexibleTopAppBar(
                title = { ScreenTitle(stringResource(R.string.archive_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.settings_back))
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
            item {
                OutlinedTextField(
                    value = query,
                    onValueChange = viewModel::setQuery,
                    leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
                    label = { Text(stringResource(R.string.archive_search)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                )
            }
            val found = results
            if (found != null && found.isEmpty()) {
                item {
                    Text(
                        stringResource(if (query.isBlank()) R.string.archive_empty else R.string.archive_no_match),
                        color = AppTheme.colors.textMuted,
                    )
                }
            }
            items(found.orEmpty(), key = { it.id }) { task ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = AppTheme.density.rowMinHeight.dp)
                        .background(AppTheme.colors.surface, AppTheme.shapes.row)
                        .clickable { onOpenTask(task.id) }
                        .padding(start = 16.dp, end = 4.dp),
                ) {
                    Column(Modifier.weight(1f).padding(vertical = 8.dp)) {
                        Text(task.title, style = MaterialTheme.typography.bodyLarge)
                        task.completedAt?.let(SyncRules::instantOf)?.let { done ->
                            Text(
                                stringResource(R.string.archive_done_on, done.atZone(ZoneId.systemDefault()).toLocalDate().format(dates)),
                                style = MaterialTheme.typography.bodySmall,
                                color = AppTheme.colors.textMuted,
                            )
                        }
                    }
                    TextButton(onClick = { viewModel.reopen(task.id) }) { Text(stringResource(R.string.archive_reopen)) }
                }
            }
        }
    }
}
