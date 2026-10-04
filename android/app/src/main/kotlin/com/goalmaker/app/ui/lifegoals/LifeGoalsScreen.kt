package com.goalmaker.app.ui.lifegoals

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MediumFlexibleTopAppBar
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.goalmaker.app.R
import com.goalmaker.app.application.planning.LifeGoalItem
import com.goalmaker.app.ui.components.AppSnackbarHost
import com.goalmaker.app.ui.components.ScreenTitle
import com.goalmaker.app.ui.nav.PlaceNavigationIcon
import com.goalmaker.app.ui.theme.AppTheme
import kotlinx.coroutines.launch

/**
 * The Life goals place (docs/life-goals.md, M9-02): a card per open life goal in the owner's order,
 * the achieved and dropped ones folded below, and New life goal at the bottom. [focus] scrolls to a
 * life goal another screen asked for (the why reminder, the widget), and [onFocused] says it did.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LifeGoalsScreen(
    viewModel: LifeGoalsViewModel,
    onBack: (() -> Unit)?,
    actions: @Composable () -> Unit,
    focus: String? = null,
    onFocused: () -> Unit = {},
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val snackbars = remember { SnackbarHostState() }
    val resources = LocalResources.current
    val scope = rememberCoroutineScope()
    var adding by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<LifeGoalItem?>(null) }
    var deleting by remember { mutableStateOf<LifeGoalItem?>(null) }
    var showClosed by rememberSaveable { mutableStateOf(false) }
    val list = androidx.compose.foundation.lazy.rememberLazyListState()

    LaunchedEffect(viewModel) {
        viewModel.undo.collect { event ->
            val message = resources.getString(
                when (event.kind) {
                    LifeGoalUndo.Kind.ACHIEVED -> R.string.life_goals_achieved_message
                    LifeGoalUndo.Kind.DROPPED -> R.string.life_goals_dropped_message
                    LifeGoalUndo.Kind.DELETED -> R.string.life_goals_deleted_message
                },
                event.title,
            )
            val result = snackbars.showSnackbar(message, actionLabel = resources.getString(R.string.lists_undo), duration = SnackbarDuration.Short)
            if (result == SnackbarResult.ActionPerformed) event.undo()
        }
    }
    LaunchedEffect(focus, state.loaded) {
        if (focus != null && state.loaded) {
            val index = state.open.indexOfFirst { it.goal.id == focus }
            if (index >= 0) list.animateScrollToItem(index)
            onFocused()
        }
    }

    val pictureFailed: () -> Unit = {
        scope.launch { snackbars.showSnackbar(resources.getString(R.string.life_goals_picture_failed)) }
    }
    if (adding) {
        LifeGoalSheet(viewModel, initial = null, pictures = emptyList(), pictureVersion = state.pictureVersion, onDismiss = { adding = false }, onPictureFailed = pictureFailed)
    }
    editing?.let { goal ->
        val pictures = (state.open + state.closed).firstOrNull { it.goal.id == goal.id }?.pictures.orEmpty()
        LifeGoalSheet(viewModel, initial = goal, pictures = pictures, pictureVersion = state.pictureVersion, onDismiss = { editing = null }, onPictureFailed = pictureFailed)
    }
    deleting?.let { goal ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text(stringResource(R.string.life_goals_delete_question, goal.title)) },
            text = { Text(stringResource(R.string.life_goals_delete_detail)) },
            confirmButton = {
                TextButton(onClick = {
                    deleting = null
                    viewModel.delete(goal)
                }) { Text(stringResource(R.string.life_goals_delete), color = AppTheme.colors.danger) }
            },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text(stringResource(R.string.life_goals_cancel)) } },
        )
    }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        snackbarHost = { AppSnackbarHost(snackbars) },
        topBar = {
            MediumFlexibleTopAppBar(
                title = { ScreenTitle(stringResource(R.string.life_goals_title)) },
                navigationIcon = { PlaceNavigationIcon(onBack) },
                actions = { actions() },
                scrollBehavior = scrollBehavior,
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { adding = true },
                icon = { Icon(Icons.Outlined.Add, contentDescription = null) },
                text = { Text(stringResource(R.string.life_goals_add)) },
            )
        },
    ) { padding ->
        if (!state.loaded) return@Scaffold
        LazyColumn(
            state = list,
            contentPadding = PaddingValues(start = AppTheme.density.pagePadding.dp, end = AppTheme.density.pagePadding.dp, top = 8.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(AppTheme.density.rowGap.dp + 6.dp),
            modifier = Modifier.fillMaxSize().padding(padding),
        ) {
            if (state.empty) {
                item("empty") {
                    Text(
                        stringResource(R.string.life_goals_empty),
                        style = MaterialTheme.typography.bodyLarge,
                        color = AppTheme.colors.textMuted,
                        modifier = Modifier.padding(8.dp),
                    )
                }
            }
            itemsIndexed(state.open, key = { _, row -> row.goal.id }) { index, row ->
                LifeGoalCard(
                    row = row,
                    pictureVersion = state.pictureVersion,
                    loadPicture = viewModel::picture,
                    canMoveUp = index > 0,
                    canMoveDown = index < state.open.lastIndex,
                    onEdit = { editing = row.goal },
                    onAchieve = { viewModel.achieve(row.goal) },
                    onDrop = { viewModel.drop(row.goal) },
                    onReopen = { viewModel.reopen(row.goal) },
                    onMove = { viewModel.move(row.goal, it) },
                    onDelete = { deleting = row.goal },
                    modifier = Modifier.animateItem(),
                )
            }
            if (state.closed.isNotEmpty()) {
                item("closed") {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                            .semantics { role = Role.Button }
                            .clickable { showClosed = !showClosed },
                    ) {
                        Text(
                            "${stringResource(R.string.life_goals_closed)} ${state.closed.size}",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = AppTheme.colors.textMuted,
                        )
                        Spacer(Modifier.width(4.dp))
                        Icon(if (showClosed) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore, contentDescription = null, tint = AppTheme.colors.textMuted)
                    }
                }
                if (showClosed) {
                    items(state.closed, key = { it.goal.id }) { row ->
                        LifeGoalCard(
                            row = row,
                            pictureVersion = state.pictureVersion,
                            loadPicture = viewModel::picture,
                            canMoveUp = false,
                            canMoveDown = false,
                            onEdit = { editing = row.goal },
                            onAchieve = {},
                            onDrop = {},
                            onReopen = { viewModel.reopen(row.goal) },
                            onMove = {},
                            onDelete = { deleting = row.goal },
                            modifier = Modifier.animateItem(),
                        )
                    }
                }
            }
        }
    }
}
