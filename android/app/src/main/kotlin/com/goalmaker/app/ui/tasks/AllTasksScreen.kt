package com.goalmaker.app.ui.tasks

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
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MediumFlexibleTopAppBar
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.goalmaker.app.R
import com.goalmaker.app.application.planning.ProjectRules
import com.goalmaker.app.application.planning.TaskItem
import com.goalmaker.app.ui.components.GoalMakerCheckbox
import com.goalmaker.app.ui.components.ProjectChip
import com.goalmaker.app.ui.components.ScreenTitle
import com.goalmaker.app.ui.lists.ListFilterRow
import com.goalmaker.app.ui.nav.PlaceNavigationIcon
import com.goalmaker.app.ui.theme.AppTheme
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * The All tasks place (docs/all-tasks.md): every open task grouped by area, the no-area group last. The
 * search box and the area and tag filter narrow it, a chip adds project items, a tick completes a task
 * and a tap opens it.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AllTasksScreen(
    viewModel: AllTasksViewModel,
    onBack: (() -> Unit)?,
    onOpenTask: (String) -> Unit,
    actions: @Composable () -> Unit = {},
    onOpenProject: ((String) -> Unit)? = null,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val locale = LocalConfiguration.current.locales[0]
    val dates = remember(locale) { DateTimeFormatter.ofPattern("EEE d MMM", locale) }
    val today = LocalDate.now()

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            MediumFlexibleTopAppBar(
                title = { ScreenTitle(stringResource(R.string.all_tasks_title)) },
                navigationIcon = { PlaceNavigationIcon(onBack) },
                actions = { actions() },
                scrollBehavior = scrollBehavior,
            )
        },
    ) { padding ->
        LazyColumn(
            contentPadding = PaddingValues(horizontal = AppTheme.density.pagePadding.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(AppTheme.density.rowGap.dp),
            modifier = Modifier.fillMaxSize().padding(padding),
        ) {
            item(key = "search") {
                OutlinedTextField(
                    value = state.query,
                    onValueChange = viewModel::setQuery,
                    leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
                    label = { Text(stringResource(R.string.all_tasks_search)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                )
            }
            item(key = "filter") {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    FilterChip(
                        selected = state.projectItems,
                        onClick = { viewModel.showProjectItems(!state.projectItems) },
                        label = { Text(stringResource(R.string.all_tasks_project_items)) },
                        modifier = Modifier.padding(end = 8.dp),
                    )
                    ListFilterRow(state.choices, onArea = viewModel::filterByArea, onTag = viewModel::filterByTag)
                }
            }
            val groups = state.groups
            if (groups != null && groups.isEmpty()) {
                item(key = "empty") {
                    val searching = state.query.isNotBlank() || !state.choices.filter.isEmpty
                    Text(
                        stringResource(if (searching) R.string.all_tasks_no_match else R.string.all_tasks_empty),
                        color = AppTheme.colors.textMuted,
                        modifier = Modifier.padding(vertical = 24.dp),
                    )
                }
            }
            groups.orEmpty().forEach { group ->
                val area = group.areaId?.let(state.areas::get)
                item(key = "area-${group.areaId}") {
                    Text(
                        area?.let { listOfNotNull(it.emoji, it.name).joinToString(" ") } ?: stringResource(R.string.all_tasks_no_area),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(top = 16.dp, bottom = 4.dp).semantics { heading() },
                    )
                }
                items(group.tasks, key = { it.id }) { task ->
                    TaskRow(task, today, dates, state, onOpenTask, onOpenProject, viewModel::complete)
                }
            }
        }
    }
}

@Composable
private fun TaskRow(
    task: TaskItem,
    today: LocalDate,
    dates: DateTimeFormatter,
    state: AllTasksUiState,
    onOpenTask: (String) -> Unit,
    onOpenProject: ((String) -> Unit)?,
    onComplete: (String) -> Unit,
) {
    val date = task.deadline ?: task.plannedDate
    val late = date != null && date < today
    val dateLine = when {
        date == null -> null
        task.deadline != null -> stringResource(R.string.all_tasks_due, date.format(dates))
        else -> date.format(dates)
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = AppTheme.density.rowMinHeight.dp)
            .background(AppTheme.colors.surface, AppTheme.shapes.row)
            .clickable { onOpenTask(task.id) }
            .padding(start = 4.dp, end = 12.dp),
    ) {
        GoalMakerCheckbox(checked = false, onCheckedChange = { if (it) onComplete(task.id) })
        Column(Modifier.weight(1f).padding(vertical = 8.dp)) {
            Text(task.title, style = MaterialTheme.typography.bodyLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (dateLine != null) {
                Text(
                    dateLine,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (late) AppTheme.colors.danger else AppTheme.colors.textMuted,
                )
            }
            task.projectId?.let(state.projects::get)?.let { project ->
                ProjectChip(
                    project,
                    task.itemType,
                    onOpenProject?.let { open -> { open(project.id) } },
                    Modifier.padding(top = 4.dp),
                    ProjectRules.itemIdOf(task, project),
                )
            }
        }
    }
}
