package com.goalmaker.app.ui.habits

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.LocalFireDepartment
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MediumFlexibleTopAppBar
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
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
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.goalmaker.app.R
import com.goalmaker.app.application.planning.HabitItem
import com.goalmaker.app.application.planning.HabitRules
import com.goalmaker.app.ui.components.ConfettiBurst
import com.goalmaker.app.ui.components.ScreenTitle
import com.goalmaker.app.ui.lists.SectionHeader
import com.goalmaker.app.ui.theme.AppTheme
import kotlinx.coroutines.launch

/**
 * The Habits screen (docs/habits.md, spec stories 36 to 42): every habit with today's ring, its streak
 * and its heatmap. A tap on the ring checks in (an amount asks for its value); a tap on the card edits.
 * A streak reaching a milestone gets confetti unless motion is reduced.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HabitsScreen(viewModel: HabitsViewModel, onBack: () -> Unit) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val scope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current
    // null: no dialog; a habit with an empty id: a new one.
    var editing by remember { mutableStateOf<HabitItem?>(null) }
    var logging by remember { mutableStateOf<HabitItem?>(null) }
    var archivedOpen by rememberSaveable { mutableStateOf(false) }

    val reduceMotion = AppTheme.reduceMotion
    var seen by remember { mutableStateOf<Set<String>?>(null) }
    var bursts by remember { mutableIntStateOf(0) }
    LaunchedEffect(state.loaded, state.milestones) {
        if (!state.loaded) return@LaunchedEffect
        val before = seen
        seen = state.milestones
        if (before != null && !reduceMotion && !before.containsAll(state.milestones)) bursts++
    }

    fun tap(row: HabitRow) {
        haptics.performHapticFeedback(HapticFeedbackType.ToggleOn)
        scope.launch { if (!viewModel.tap(row.habit.id)) logging = row.habit }
    }

    Box(Modifier.fillMaxSize()) {
        Scaffold(
            modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
            topBar = {
                MediumFlexibleTopAppBar(
                    title = { ScreenTitle(stringResource(R.string.habits_title)) },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.settings_back))
                        }
                    },
                    actions = {
                        IconButton(onClick = { editing = HabitItem("", "", state.today) }) {
                            Icon(Icons.Outlined.Add, contentDescription = stringResource(R.string.habits_add))
                        }
                    },
                    scrollBehavior = scrollBehavior,
                )
            },
        ) { padding ->
            if (state.loaded) {
                LazyColumn(
                    contentPadding = PaddingValues(horizontal = AppTheme.density.pagePadding.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(AppTheme.density.rowGap.dp + 4.dp),
                    modifier = Modifier.fillMaxSize().padding(padding),
                ) {
                    @Composable
                    fun Card(row: HabitRow, modifier: Modifier) = HabitCard(
                        row = row,
                        onTap = { tap(row) },
                        onEdit = { editing = row.habit },
                        onLog = { logging = row.habit },
                        viewModel = viewModel,
                        modifier = modifier,
                    )
                    if (state.active.isEmpty()) {
                        item(key = "empty") {
                            Text(
                                stringResource(R.string.habits_empty),
                                style = MaterialTheme.typography.bodyLarge,
                                color = AppTheme.colors.textMuted,
                                modifier = Modifier.padding(8.dp),
                            )
                        }
                    }
                    items(state.active, key = { it.habit.id }) { row -> Card(row, Modifier.animateItem()) }
                    item(key = "add") {
                        TextButton(onClick = { editing = HabitItem("", "", state.today) }) {
                            Icon(Icons.Outlined.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                            Text(stringResource(R.string.habits_add), modifier = Modifier.padding(start = 8.dp))
                        }
                    }
                    if (state.archived.isNotEmpty()) {
                        item(key = "h-archived") {
                            SectionHeader(
                                text = pluralStringResource(R.plurals.habits_archived, state.archived.size, state.archived.size),
                                expanded = archivedOpen,
                                onToggle = { archivedOpen = !archivedOpen },
                            )
                        }
                        if (archivedOpen) items(state.archived, key = { "archived-" + it.habit.id }) { row -> Card(row, Modifier.animateItem()) }
                    }
                }
            }
        }
        if (bursts > 0) {
            key(bursts) { ConfettiBurst(onFinished = { bursts = 0 }) }
        }
    }

    editing?.let { habit ->
        HabitDialog(
            habit = habit,
            goals = state.goals,
            onSave = { draft -> viewModel.save(habit.id.ifEmpty { null }, draft) },
            onDelete = if (habit.id.isEmpty()) null else ({ viewModel.delete(habit.id) }),
            onDismiss = { editing = null },
        )
    }
    logging?.let { habit ->
        AmountDialog(habit, onLog = { amount -> viewModel.checkIn(habit.id, amount) }, onDismiss = { logging = null })
    }
}

/** A habit: its ring (a tap checks in), name, streak and where today stands, then its heatmap. */
@Composable
private fun HabitCard(
    row: HabitRow,
    onTap: () -> Unit,
    onEdit: () -> Unit,
    onLog: () -> Unit,
    viewModel: HabitsViewModel,
    modifier: Modifier = Modifier,
) {
    val habit = row.habit
    val checkIn = stringResource(R.string.habits_check_in, habit.name)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(AppTheme.colors.surface, AppTheme.shapes.card)
            .clickable(onClick = onEdit)
            .padding(start = 12.dp, end = 4.dp, top = 10.dp, bottom = 12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            val active = !habit.archived && !row.paused && row.ring != null
            HabitRing(
                fraction = (row.ring ?: 0.0).toFloat(),
                modifier = Modifier
                    .clip(CircleShape)
                    .clickable(enabled = active, role = Role.Button, onClick = onTap)
                    .semantics { contentDescription = checkIn },
            ) {
                when {
                    habit.emoji != null -> Text(habit.emoji, style = MaterialTheme.typography.titleMedium)
                    row.done -> Icon(Icons.Outlined.Check, contentDescription = null, tint = AppTheme.colors.accent, modifier = Modifier.size(22.dp))
                }
            }
            Column(Modifier.weight(1f).padding(start = 12.dp)) {
                Text(habit.name, style = MaterialTheme.typography.bodyLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(
                    stringResource(R.string.habits_line, cadenceText(habit), statusText(row)),
                    style = MaterialTheme.typography.bodySmall,
                    color = AppTheme.colors.textMuted,
                )
                streakText(row)?.let { streak ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.LocalFireDepartment, contentDescription = null, tint = AppTheme.colors.accent, modifier = Modifier.size(14.dp))
                        Text(streak, style = MaterialTheme.typography.labelMedium, color = AppTheme.colors.accent, modifier = Modifier.padding(start = 2.dp))
                    }
                }
                row.goalTitle?.let {
                    Text(stringResource(R.string.habits_serves, it), style = MaterialTheme.typography.bodySmall, color = AppTheme.colors.textMuted)
                }
            }
            HabitMenu(row, onEdit, onLog, viewModel)
        }
        HabitHeatmap(row, Modifier.padding(top = 10.dp, end = 8.dp))
    }
}

@Composable
private fun HabitMenu(row: HabitRow, onEdit: () -> Unit, onLog: () -> Unit, viewModel: HabitsViewModel) {
    val habit = row.habit
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }) {
            Icon(Icons.Outlined.MoreVert, contentDescription = stringResource(R.string.habits_more, habit.name))
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            @Composable
            fun Choice(label: String, danger: Boolean = false, action: () -> Unit) = DropdownMenuItem(
                text = { Text(label, color = if (danger) AppTheme.colors.danger else MaterialTheme.colorScheme.onSurface) },
                onClick = {
                    open = false
                    action()
                },
            )
            Choice(stringResource(R.string.habits_edit_menu), action = onEdit)
            if (!habit.archived) {
                if (habit.measure != HabitRules.CHECK && !row.paused) Choice(stringResource(R.string.habits_log), action = onLog)
                if (row.value > 0.0) Choice(stringResource(R.string.habits_clear)) { viewModel.clearToday(habit.id) }
                if (row.skipped) {
                    Choice(stringResource(R.string.habits_unskip)) { viewModel.skip(habit.id, false) }
                } else if (!row.paused) {
                    val skip = when (habit.cadence) {
                        HabitRules.PER_WEEK -> R.string.habits_skip_week
                        HabitRules.PER_MONTH -> R.string.habits_skip_month
                        else -> R.string.habits_skip_day
                    }
                    Choice(stringResource(skip)) { viewModel.skip(habit.id, true) }
                }
                if (row.paused) {
                    Choice(stringResource(R.string.habits_resume)) { viewModel.resume(habit.id) }
                } else {
                    Choice(stringResource(R.string.habits_pause)) { viewModel.pause(habit.id) }
                }
                Choice(stringResource(R.string.habits_archive)) { viewModel.setArchived(habit.id, true) }
            } else {
                Choice(stringResource(R.string.habits_unarchive)) { viewModel.setArchived(habit.id, false) }
            }
            Choice(stringResource(R.string.habits_delete), danger = true) { viewModel.delete(habit.id) }
        }
    }
}
