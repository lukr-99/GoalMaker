package com.goalmaker.app.ui.projects

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.ArrowDropDown
import androidx.compose.material.icons.outlined.BugReport
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.PauseCircle
import androidx.compose.material.icons.outlined.PlayCircle
import androidx.compose.material.icons.outlined.TaskAlt
import androidx.compose.material.icons.outlined.ViewAgenda
import androidx.compose.material.icons.outlined.ViewColumn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MediumFlexibleTopAppBar
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.goalmaker.app.R
import com.goalmaker.app.application.planning.AreaItem
import com.goalmaker.app.application.planning.ProjectColumn
import com.goalmaker.app.application.planning.ProjectDraft
import com.goalmaker.app.application.planning.ProjectItem
import com.goalmaker.app.application.planning.ProjectRules
import com.goalmaker.app.application.planning.TaskItem
import com.goalmaker.app.application.planning.TaskState
import com.goalmaker.app.domain.settings.BoardView
import com.goalmaker.app.ui.components.AppSnackbarHost
import com.goalmaker.app.ui.components.ChoiceChip
import com.goalmaker.app.ui.components.ScreenTitle
import com.goalmaker.app.ui.lists.ListFilterRow
import com.goalmaker.app.ui.lists.SectionHeader
import com.goalmaker.app.ui.lists.UndoEvent
import com.goalmaker.app.ui.nav.AppMark
import com.goalmaker.app.ui.nav.PlaceNavigationIcon
import com.goalmaker.app.ui.theme.AppTheme

/** The projects and the board of the one on show (docs/projects.md). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProjectsScreen(
    viewModel: ProjectsViewModel,
    onOpenTask: (String) -> Unit,
    actions: @Composable () -> Unit,
    onBack: (() -> Unit)? = null,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    var editing by remember { mutableStateOf<ProjectItem?>(null) }
    var adding by remember { mutableStateOf(false) }
    var addingItem by remember { mutableStateOf(false) }
    var showArchived by remember { mutableStateOf(false) }
    var tab by rememberSaveable { mutableStateOf(ProjectRules.TODO) }
    val snackbars = remember { SnackbarHostState() }
    val resources = LocalResources.current

    // Finishing an item or taking it out of the project can be taken back, as on the lists.
    LaunchedEffect(viewModel) {
        viewModel.undo.collect { event ->
            val message = resources.getString(
                when (event.kind) {
                    UndoEvent.Kind.DONE -> R.string.lists_done_message
                    UndoEvent.Kind.ARCHIVED -> R.string.projects_archived_message
                    else -> R.string.projects_removed_message
                },
                event.title,
            )
            val result = snackbars.showSnackbar(message, actionLabel = resources.getString(R.string.lists_undo), duration = SnackbarDuration.Short)
            if (result == SnackbarResult.ActionPerformed) event.undo()
        }
    }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        snackbarHost = { AppSnackbarHost(snackbars) },
        topBar = {
            MediumFlexibleTopAppBar(
                title = { ScreenTitle(stringResource(R.string.projects_title)) },
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
            if (!state.anyProject) {
                item("empty") {
                    Text(
                        stringResource(R.string.projects_empty),
                        style = MaterialTheme.typography.bodyLarge,
                        color = AppTheme.colors.textMuted,
                        modifier = Modifier.padding(8.dp),
                    )
                }
                item("add-project") {
                    TextButton(onClick = { adding = true }) {
                        Icon(Icons.Outlined.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                        Text(stringResource(R.string.projects_add), modifier = Modifier.padding(start = 8.dp))
                    }
                }
                return@LazyColumn
            }

            // The area and tag filter narrows the projects and the board, as it narrows the lists.
            item("filter") {
                ListFilterRow(state.filter, onArea = viewModel::filterByArea, onTag = viewModel::filterByTag)
            }

            item("picker") {
                // A new project starts beside the picker, where the projects are chosen.
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (state.projects.isEmpty()) {
                        Text(
                            stringResource(R.string.projects_none_match),
                            style = MaterialTheme.typography.bodyMedium,
                            color = AppTheme.colors.textMuted,
                            modifier = Modifier.weight(1f).padding(8.dp),
                        )
                    } else {
                        ProjectPicker(state, onPick = viewModel::select, modifier = Modifier.weight(1f))
                    }
                    IconButton(onClick = { adding = true }) {
                        Icon(Icons.Outlined.Add, contentDescription = stringResource(R.string.projects_add))
                    }
                }
            }

            state.selected?.let { project ->
                item("about") { ProjectCard(project, onEdit = { editing = project }) }
                item("add-item") {
                    TextButton(onClick = { addingItem = true }) {
                        Icon(Icons.Outlined.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                        Text(stringResource(R.string.projects_add_item), modifier = Modifier.padding(start = 8.dp))
                    }
                }
                item("made-by") {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        MakerSwitch(state.madeBy, onPick = viewModel::showMadeBy, modifier = Modifier.weight(1f))
                        ViewSwitch(state.view, onPick = viewModel::showView)
                    }
                }
                // The phone shows one column at a time behind tabs, or the columns stacked with each one
                // folding away, so a long Done no longer pushes everything else down (docs/projects.md).
                val shown = if (state.view == BoardView.COLUMNS) {
                    item("tabs") { ColumnTabs(state.board, tab, onPick = { tab = it }) }
                    state.board.filter { it.column == tab }
                } else {
                    state.board
                }
                shown.forEach { column ->
                    val open = state.view == BoardView.COLUMNS || column.column !in state.collapsed
                    if (state.view == BoardView.LIST) {
                        item("h-${column.column}") {
                            SectionHeader(
                                text = columnName(column.column) + " · " + column.items.size,
                                expanded = open,
                                onToggle = { viewModel.toggleColumn(column.column) },
                            )
                        }
                    }
                    if (!open) return@forEach
                    if (column.items.isEmpty()) {
                        item("empty-${column.column}") {
                            Text(
                                stringResource(R.string.projects_column_empty),
                                style = MaterialTheme.typography.bodySmall,
                                color = AppTheme.colors.textMuted,
                                modifier = Modifier.padding(start = 8.dp, bottom = 4.dp),
                            )
                        }
                    }
                    items(column.items, key = { "item-" + it.id }) { task ->
                        ItemRow(
                            task = task,
                            columns = column,
                            onOpen = { onOpenTask(task.id) },
                            onMove = { to -> viewModel.move(task, to) },
                            onPriority = { priority -> viewModel.setPriority(task.id, priority) },
                            onArchive = { viewModel.archive(task) },
                            onRemove = { viewModel.removeFromProject(task) },
                        )
                    }
                    if (column.column == ProjectRules.DONE && state.archived.isNotEmpty()) {
                        item("archived") { ArchivedLink(state.archived.size, onOpen = { showArchived = true }) }
                    }
                }
            }
        }
    }

    // The list closes by itself once the last item is back on the board.
    val nothingArchived = state.archived.isEmpty()
    LaunchedEffect(nothingArchived) { if (nothingArchived) showArchived = false }
    if (showArchived && !nothingArchived) {
        ArchivedDialog(
            items = state.archived,
            onOpen = { task ->
                showArchived = false
                onOpenTask(task.id)
            },
            onPutBack = viewModel::putBack,
            onReopen = { task -> viewModel.move(task, ProjectRules.TODO) },
            onDismiss = { showArchived = false },
        )
    }

    if (adding || editing != null) {
        ProjectDialog(
            project = editing,
            areas = state.filter.areas,
            newArea = state.filter.filter.areaId,
            onSave = { draft, archiveAfterDays ->
                val current = editing
                if (current == null) {
                    viewModel.addProject(draft, archiveAfterDays)
                } else {
                    viewModel.updateProject(current.id, draft, archiveAfterDays)
                }
                adding = false
                editing = null
            },
            onDelete = editing?.let { project ->
                {
                    viewModel.deleteProject(project.id)
                    editing = null
                }
            },
            onDismiss = {
                adding = false
                editing = null
            },
        )
    }

    if (addingItem) {
        ItemDialog(
            onSave = { title, type, column, priority, notes ->
                viewModel.addItem(title, type, column, priority, notes)
                addingItem = false
            },
            onDismiss = { addingItem = false },
        )
    }
}

/**
 * One row naming the project on show, which drops a menu of the rest. A row of chips grew downwards
 * with every project added and pushed the board off the phone, so the picker keeps to its one line
 * however many projects there are. Each choice carries how many items are still waiting on it.
 */
@Composable
private fun ProjectPicker(state: ProjectsUiState, onPick: (String) -> Unit, modifier: Modifier = Modifier) {
    var open by remember { mutableStateOf(false) }
    Box(modifier) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .background(AppTheme.colors.surface, AppTheme.shapes.row)
                .clickable { open = true }
                .padding(start = 12.dp, end = 8.dp, top = 10.dp, bottom = 10.dp),
        ) {
            state.selected?.let { StatusMark(it.status, Modifier.padding(end = 8.dp)) }
            Text(
                state.selected?.name ?: stringResource(R.string.projects_pick),
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                modifier = Modifier.weight(1f),
            )
            WaitingCount(state.selected?.let { state.openCounts[it.id] } ?: 0)
            Icon(
                Icons.Outlined.ArrowDropDown,
                contentDescription = stringResource(R.string.projects_pick),
                modifier = Modifier.padding(start = 4.dp),
            )
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            state.projects.forEach { project ->
                DropdownMenuItem(
                    text = { Text(project.name, color = if (project.status == ProjectRules.ACTIVE) Color.Unspecified else AppTheme.colors.textMuted) },
                    leadingIcon = { StatusMark(project.status) },
                    trailingIcon = { WaitingCount(state.openCounts[project.id] ?: 0) },
                    onClick = {
                        open = false
                        onPick(project.id)
                    },
                )
            }
        }
    }
}

/** Whose items the board shows: everyone's, the owner's, or Claude's (docs/projects.md). */
@Composable
private fun MakerSwitch(madeBy: String, onPick: (String) -> Unit, modifier: Modifier = Modifier) {
    // A narrow phone scrolls the chips sideways rather than cutting the last one off.
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = modifier.horizontalScroll(rememberScrollState()),
    ) {
        Text(
            stringResource(R.string.projects_made_by),
            style = MaterialTheme.typography.labelMedium,
            color = AppTheme.colors.textMuted,
        )
        ProjectRules.MAKER_FILTERS.forEach { filter ->
            ChoiceChip(selected = madeBy == filter, onClick = { onPick(filter) }, label = makerName(filter))
        }
    }
}

/** Switches the board between one column at a time and the stacked list; the icon shows where it leads. */
@Composable
private fun ViewSwitch(view: BoardView, onPick: (BoardView) -> Unit) {
    val toList = view == BoardView.COLUMNS
    IconButton(onClick = { onPick(if (toList) BoardView.LIST else BoardView.COLUMNS) }) {
        Icon(
            if (toList) Icons.Outlined.ViewAgenda else Icons.Outlined.ViewColumn,
            contentDescription = stringResource(if (toList) R.string.projects_view_list else R.string.projects_view_columns),
        )
    }
}

/** The four columns as tabs, each with how many items it holds; the one picked is the one on show. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ColumnTabs(board: List<ProjectColumn>, selected: String, onPick: (String) -> Unit) {
    val index = board.indexOfFirst { it.column == selected }.coerceAtLeast(0)
    PrimaryScrollableTabRow(
        selectedTabIndex = index,
        edgePadding = 0.dp,
        containerColor = Color.Transparent,
        contentColor = AppTheme.colors.accent,
        indicator = {
            TabRowDefaults.PrimaryIndicator(
                modifier = Modifier.tabIndicatorOffset(index, matchContentSize = true),
                width = Dp.Unspecified,
                color = AppTheme.colors.accent,
            )
        },
    ) {
        board.forEach { column ->
            Tab(
                selected = column.column == selected,
                onClick = { onPick(column.column) },
                selectedContentColor = AppTheme.colors.accent,
                unselectedContentColor = AppTheme.colors.textMuted,
                text = {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(columnName(column.column), maxLines = 1)
                        Text(column.items.size.toString(), style = MaterialTheme.typography.labelMedium)
                    }
                },
            )
        }
    }
}

/** The line at the end of Done that says how many items left the board, and opens them. */
@Composable
private fun ArchivedLink(count: Int, onOpen: () -> Unit) {
    TextButton(onClick = onOpen) {
        Icon(Icons.Outlined.Inventory2, contentDescription = null, modifier = Modifier.size(18.dp))
        Text(pluralStringResource(R.plurals.projects_archived_count, count, count), modifier = Modifier.padding(start = 8.dp))
    }
}

/**
 * The done items that left the board. One archived by hand can be put back in Done; one that left
 * with time would only leave again, so it offers to reopen it in To do instead.
 */
@Composable
private fun ArchivedDialog(
    items: List<TaskItem>,
    onOpen: (TaskItem) -> Unit,
    onPutBack: (TaskItem) -> Unit,
    onReopen: (TaskItem) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.projects_archived_title)) },
        text = {
            LazyColumn {
                item("hint") {
                    Text(
                        stringResource(R.string.projects_archived_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = AppTheme.colors.textMuted,
                        modifier = Modifier.padding(bottom = 8.dp),
                    )
                }
                items(items, key = { it.id }) { task ->
                    val byHand = task.boardArchivedAt != null
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                        Text(
                            task.title,
                            style = MaterialTheme.typography.bodyLarge,
                            maxLines = 2,
                            modifier = Modifier.weight(1f).clickable { onOpen(task) }.padding(vertical = 12.dp),
                        )
                        TextButton(onClick = { if (byHand) onPutBack(task) else onReopen(task) }) {
                            Text(stringResource(if (byHand) R.string.projects_put_back else R.string.projects_reopen))
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.projects_close)) } },
    )
}

/** How many items a board still has waiting, or nothing at all when it is clear. */
@Composable
private fun WaitingCount(waiting: Int) {
    if (waiting == 0) return
    Text(
        waiting.toString(),
        style = MaterialTheme.typography.labelLarge,
        color = AppTheme.colors.accent,
    )
}

@Composable
private fun ProjectCard(project: ProjectItem, onEdit: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(AppTheme.colors.surface, AppTheme.shapes.card)
            .clickable(onClick = onEdit)
            .padding(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(project.name, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            StatusMark(project.status, Modifier.padding(end = 4.dp))
            Text(
                statusName(project.status),
                style = MaterialTheme.typography.labelMedium,
                color = statusColor(project.status),
            )
        }
        if (project.description.isNotBlank()) {
            Text(
                project.description,
                style = MaterialTheme.typography.bodyMedium,
                color = AppTheme.colors.textMuted,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
        listOfNotNull(project.repositoryUrl, project.localFolder).forEach { line ->
            Text(line, style = MaterialTheme.typography.labelSmall, color = AppTheme.colors.textMuted, maxLines = 1)
        }
    }
}

@Composable
private fun ItemRow(
    task: TaskItem,
    columns: ProjectColumn,
    onOpen: () -> Unit,
    onMove: (String) -> Unit,
    onPriority: (String) -> Unit,
    onArchive: () -> Unit,
    onRemove: () -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .background(AppTheme.colors.surface, AppTheme.shapes.row)
            .clickable(onClick = onOpen)
            .padding(start = 12.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
    ) {
        Box(
            Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(priorityColor(task.priority)),
        )
        Column(Modifier.weight(1f).padding(start = 10.dp)) {
            Text(
                task.title,
                style = MaterialTheme.typography.bodyLarge,
                color = if (task.state == TaskState.DROPPED) AppTheme.colors.textMuted else AppTheme.colors.text,
                maxLines = 2,
            )
            // An idea and a bug carry their own icon and colour, so a board reads at a glance.
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    typeIcon(task.itemType),
                    contentDescription = null,
                    tint = typeColor(task.itemType),
                    modifier = Modifier.size(14.dp),
                )
                Text(
                    typeName(task.itemType),
                    style = MaterialTheme.typography.labelSmall,
                    color = typeColor(task.itemType),
                    modifier = Modifier.padding(start = 4.dp),
                )
                task.plannedDate?.let { day ->
                    Text(
                        " · " + day.toString(),
                        style = MaterialTheme.typography.labelSmall,
                        color = AppTheme.colors.textMuted,
                    )
                }
                if (task.madeBy == ProjectRules.CLAUDE) {
                    Text(
                        " · " + stringResource(R.string.projects_by_claude),
                        style = MaterialTheme.typography.labelSmall,
                        color = AppTheme.colors.textMuted,
                    )
                }
            }
        }
        Box {
            IconButton(onClick = { menu = true }) {
                Icon(Icons.Outlined.MoreVert, contentDescription = stringResource(R.string.projects_item_menu))
            }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                ProjectRules.COLUMNS.filterNot { it == columns.column }.forEach { column ->
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.projects_move_to, columnName(column))) },
                        onClick = {
                            menu = false
                            onMove(column)
                        },
                    )
                }
                ProjectRules.PRIORITIES.filterNot { it == task.priority }.forEach { priority ->
                    DropdownMenuItem(
                        text = { Text(priorityName(priority)) },
                        onClick = {
                            menu = false
                            onPriority(priority)
                        },
                    )
                }
                // Only a done item can leave the board by hand.
                if (task.state == TaskState.DONE) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.projects_archive)) },
                        onClick = {
                            menu = false
                            onArchive()
                        },
                    )
                }
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.projects_remove_item)) },
                    onClick = {
                        menu = false
                        onRemove()
                    },
                )
            }
        }
    }
}

@Composable
private fun ProjectDialog(
    project: ProjectItem?,
    areas: List<AreaItem>,
    newArea: String?,
    onSave: (ProjectDraft, Int?) -> Unit,
    onDelete: (() -> Unit)?,
    onDismiss: () -> Unit,
) {
    var name by remember { mutableStateOf(project?.name.orEmpty()) }
    var description by remember { mutableStateOf(project?.description.orEmpty()) }
    var repository by remember { mutableStateOf(project?.repositoryUrl.orEmpty()) }
    var folder by remember { mutableStateOf(project?.localFolder.orEmpty()) }
    var status by remember { mutableStateOf(project?.status ?: ProjectRules.ACTIVE) }
    // A new project made while an area filter is on starts in that area, so it stays in sight.
    var area by remember { mutableStateOf(if (project == null) newArea else project.areaId) }
    var archiveAfterDays by remember { mutableStateOf(if (project == null) ProjectRules.ARCHIVE_AFTER_DAYS else project.archiveAfterDays) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(if (project == null) R.string.projects_add else R.string.projects_edit)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Field(name, { name = it }, R.string.projects_name)
                Field(description, { description = it }, R.string.projects_description)
                Field(repository, { repository = it }, R.string.projects_repository)
                Field(folder, { folder = it }, R.string.projects_folder)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 10.dp)) {
                    listOf(ProjectRules.ACTIVE, ProjectRules.PAUSED, ProjectRules.PROJECT_DONE).forEach { choice ->
                        ChoiceChip(selected = status == choice, onClick = { status = choice }, label = statusName(choice))
                    }
                }
                // The project's area: its items without one of their own count as in it (docs/projects.md).
                if (areas.isNotEmpty()) {
                    Choices(
                        R.string.projects_area,
                        listOf(NO_AREA) + areas.map(AreaItem::id),
                        area ?: NO_AREA,
                        { choice ->
                            areas.firstOrNull { it.id == choice }?.let { listOfNotNull(it.emoji, it.name).joinToString(" ") }
                                ?: stringResource(R.string.projects_no_area)
                        },
                    ) { choice -> area = choice.takeUnless { it == NO_AREA } }
                }
                // A number set elsewhere, say by Claude, stays one of the choices rather than being lost.
                val days = (ARCHIVE_CHOICES + listOfNotNull(project?.archiveAfterDays)).distinct().sorted()
                Choices(
                    R.string.projects_archive_after,
                    days.map(Int::toString) + NEVER,
                    archiveAfterDays?.toString() ?: NEVER,
                    { choice -> archiveChoiceName(choice) },
                ) { choice -> archiveAfterDays = choice.toIntOrNull() }
                if (onDelete != null) {
                    TextButton(onClick = onDelete, modifier = Modifier.padding(top = 8.dp)) {
                        Icon(Icons.Outlined.Delete, contentDescription = null, tint = AppTheme.colors.danger, modifier = Modifier.size(18.dp))
                        Text(
                            stringResource(R.string.projects_delete),
                            color = AppTheme.colors.danger,
                            modifier = Modifier.padding(start = 8.dp),
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = name.isNotBlank(),
                onClick = {
                    onSave(
                        ProjectDraft(
                            name = name,
                            description = description,
                            areaId = area,
                            status = status,
                            repositoryUrl = repository.ifBlank { null },
                            localFolder = folder.ifBlank { null },
                            notes = project?.notes.orEmpty(),
                        ),
                        archiveAfterDays,
                    )
                },
            ) { Text(stringResource(R.string.goals_save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.goals_cancel)) } },
    )
}

/**
 * A new item with its type, column, priority and notes. The column follows the type, as an idea
 * starts in the backlog, until one is picked.
 */
@Composable
private fun ItemDialog(onSave: (title: String, type: String, column: String, priority: String, notes: String) -> Unit, onDismiss: () -> Unit) {
    var title by remember { mutableStateOf("") }
    var type by remember { mutableStateOf(ProjectRules.TASK) }
    var picked by remember { mutableStateOf<String?>(null) }
    var priority by remember { mutableStateOf(ProjectRules.NORMAL) }
    var notes by remember { mutableStateOf("") }
    val column = picked ?: ProjectRules.columnFor(type)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.projects_add_item)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Field(title, { title = it }, R.string.projects_item_title)
                Choices(R.string.projects_item_type, listOf(ProjectRules.TASK, ProjectRules.IDEA, ProjectRules.BUG), type, { typeName(it) }) { type = it }
                Choices(R.string.projects_item_column, ProjectRules.COLUMNS - ProjectRules.DONE, column, { columnName(it) }) { picked = it }
                Choices(R.string.projects_item_priority, ProjectRules.PRIORITIES, priority, { priorityName(it) }) { priority = it }
                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text(stringResource(R.string.task_notes)) },
                    minLines = 3,
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                )
            }
        },
        confirmButton = {
            TextButton(enabled = title.isNotBlank(), onClick = { onSave(title.trim(), type, column, priority, notes.trim()) }) {
                Text(stringResource(R.string.goals_save))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.goals_cancel)) } },
    )
}

/** One labelled row of chips; a narrow phone scrolls it sideways rather than cutting a chip off. */
@Composable
private fun Choices(label: Int, options: List<String>, selected: String, name: @Composable (String) -> String, onPick: (String) -> Unit) {
    Text(
        stringResource(label),
        style = MaterialTheme.typography.labelMedium,
        color = AppTheme.colors.textMuted,
        modifier = Modifier.padding(top = 12.dp),
    )
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
        options.forEach { choice ->
            ChoiceChip(selected = selected == choice, onClick = { onPick(choice) }, label = name(choice))
        }
    }
}

@Composable
private fun Field(value: String, onChange: (String) -> Unit, label: Int) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(stringResource(label)) },
        singleLine = true,
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
    )
}

/** The days offered for how long done items stay on the board, besides never (docs/projects.md). */
private val ARCHIVE_CHOICES = listOf(7, 14, 30, 90)
private const val NEVER = "never"
private const val NO_AREA = ""

@Composable
private fun archiveChoiceName(choice: String): String = choice.toIntOrNull()
    ?.let { days -> pluralStringResource(R.plurals.projects_archive_days, days, days) }
    ?: stringResource(R.string.projects_archive_never)

@Composable
private fun columnName(column: String): String = stringResource(
    when (column) {
        ProjectRules.BACKLOG -> R.string.projects_backlog
        ProjectRules.TODO -> R.string.projects_todo
        ProjectRules.DOING -> R.string.projects_doing
        else -> R.string.projects_done
    },
)

@Composable
private fun typeName(itemType: String): String = stringResource(
    when (itemType) {
        ProjectRules.IDEA -> R.string.projects_idea
        ProjectRules.BUG -> R.string.projects_bug
        else -> R.string.projects_task
    },
)

@Composable
private fun makerName(filter: String): String = stringResource(
    when (filter) {
        ProjectRules.OWNER -> R.string.projects_made_by_owner
        ProjectRules.CLAUDE -> R.string.projects_made_by_claude
        else -> R.string.projects_made_by_all
    },
)

/** The icon an item's type wears on the board: a bug, a lightbulb, or a plain task. */
private fun typeIcon(itemType: String): ImageVector = when (itemType) {
    ProjectRules.IDEA -> Icons.Outlined.Lightbulb
    ProjectRules.BUG -> Icons.Outlined.BugReport
    else -> Icons.Outlined.TaskAlt
}

/** A bug reads as a problem, an idea as something to pick up, and a task keeps the quiet colour. */
@Composable
private fun typeColor(itemType: String): Color = when (itemType) {
    ProjectRules.IDEA -> AppTheme.colors.accent
    ProjectRules.BUG -> AppTheme.colors.danger
    else -> AppTheme.colors.textMuted
}

@Composable
private fun priorityName(priority: String): String = stringResource(
    when (priority) {
        ProjectRules.URGENT -> R.string.projects_urgent
        ProjectRules.HIGH -> R.string.projects_high
        ProjectRules.LOW -> R.string.projects_low
        else -> R.string.projects_normal
    },
)

/**
 * A project's status at a glance: active plays on in the accent, paused and done step back in the muted
 * colour, each with a mark of its own so they read apart without colour too.
 */
@Composable
private fun StatusMark(status: String, modifier: Modifier = Modifier) {
    Icon(
        when (status) {
            ProjectRules.PAUSED -> Icons.Outlined.PauseCircle
            ProjectRules.PROJECT_DONE -> Icons.Outlined.CheckCircle
            else -> Icons.Outlined.PlayCircle
        },
        contentDescription = statusName(status),
        tint = statusColor(status),
        modifier = modifier.size(18.dp),
    )
}

@Composable
private fun statusColor(status: String): Color =
    if (status == ProjectRules.ACTIVE) AppTheme.colors.accent else AppTheme.colors.textMuted

@Composable
private fun statusName(status: String): String = stringResource(
    when (status) {
        ProjectRules.PAUSED -> R.string.projects_paused
        ProjectRules.PROJECT_DONE -> R.string.projects_finished
        else -> R.string.projects_active
    },
)

@Composable
private fun priorityColor(priority: String): Color = when (priority) {
    ProjectRules.URGENT -> AppTheme.colors.danger
    ProjectRules.HIGH -> AppTheme.colors.accent
    ProjectRules.LOW -> AppTheme.colors.outline
    else -> AppTheme.colors.textMuted
}
