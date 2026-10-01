package com.goalmaker.app.ui.habits

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
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
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.goalmaker.app.R
import com.goalmaker.app.application.planning.HabitGroup
import com.goalmaker.app.application.planning.HabitItem
import com.goalmaker.app.ui.components.ConfettiBurst
import com.goalmaker.app.ui.components.ScreenTitle
import com.goalmaker.app.ui.lists.SectionHeader
import com.goalmaker.app.ui.nav.PlaceNavigationIcon
import com.goalmaker.app.ui.theme.AppTheme
import kotlinx.coroutines.launch

/**
 * The Habits screen (docs/habits.md, spec stories 36 to 42; the habits prototype, option B): a summary
 * card, then the habits in their groups, Every day, Weekly and Limits, as full cards with how often,
 * pips or a bar, the week's dots and the streak; Hide done; the archived ones folded at the end. The
 * card's button checks in (an amount asks for its value), a tap on the card edits it, and a long press
 * or its menu opens the sheet where skipping lives. A streak reaching a milestone gets confetti unless
 * motion is reduced.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HabitsScreen(viewModel: HabitsViewModel, onBack: (() -> Unit)?, actions: @Composable () -> Unit = {}) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val scope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current
    // null: no dialog; a habit with an empty id: a new one.
    var editing by remember { mutableStateOf<HabitItem?>(null) }
    var logging by remember { mutableStateOf<HabitItem?>(null) }
    var menuFor by remember { mutableStateOf<String?>(null) }
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

    // The card's button: undo a skip, ask an amount for its value, or check in or add one.
    fun checkIn(row: HabitRow) {
        haptics.performHapticFeedback(HapticFeedbackType.ToggleOn)
        when {
            row.skipped -> viewModel.skip(row.habit.id, false)
            else -> scope.launch { if (!viewModel.tap(row.habit.id)) logging = row.habit }
        }
    }

    Box(Modifier.fillMaxSize()) {
        Scaffold(
            modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
            topBar = {
                MediumFlexibleTopAppBar(
                    title = { ScreenTitle(stringResource(R.string.habits_title)) },
                    navigationIcon = { PlaceNavigationIcon(onBack) },
                    actions = {
                        IconButton(onClick = { editing = HabitItem("", "", state.today) }) {
                            Icon(Icons.Outlined.Add, contentDescription = stringResource(R.string.habits_add))
                        }
                        actions()
                    },
                    scrollBehavior = scrollBehavior,
                )
            },
        ) { padding ->
            if (state.loaded) {
                LazyColumn(
                    contentPadding = PaddingValues(horizontal = AppTheme.density.pagePadding.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(AppTheme.density.rowGap.dp),
                    modifier = Modifier.fillMaxSize().padding(padding),
                ) {
                    @Composable
                    fun Card(row: HabitRow, modifier: Modifier) = HabitCard(
                        row = row,
                        today = state.today,
                        full = true,
                        onCheckIn = { checkIn(row) },
                        onMenu = { menuFor = row.habit.id },
                        onSkip = { viewModel.skip(row.habit.id, true) },
                        onClick = { editing = row.habit },
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
                    } else {
                        item(key = "summary") { HabitSummaryCard(state.summary, state.today) }
                        item(key = "bar") {
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
                                Text(
                                    stringResource(R.string.habits_page_hint),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = AppTheme.colors.textMuted,
                                    modifier = Modifier.weight(1f).padding(end = 8.dp),
                                )
                                HideDoneChip(state.hideDone, viewModel::setHideDone)
                            }
                        }
                    }
                    state.sections.forEach { section ->
                        item(key = "h-" + section.group.id) {
                            SectionHeader(stringResource(R.string.habits_group_header, stringResource(groupName(section.group)), section.total))
                        }
                        if (section.rows.isEmpty()) {
                            item(key = "done-" + section.group.id) {
                                Text(
                                    stringResource(R.string.habits_group_all_done),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = AppTheme.colors.textMuted,
                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp).animateItem(),
                                )
                            }
                        }
                        items(section.rows, key = { it.habit.id }) { row -> Card(row, Modifier.animateItem()) }
                    }
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

    menuFor?.let { id ->
        // A habit deleted elsewhere while its sheet was open takes the sheet with it.
        (state.active + state.archived).firstOrNull { it.habit.id == id }?.let { row ->
            HabitSheet(
                row = row,
                onDismiss = { menuFor = null },
                onCheckIn = { checkIn(row) },
                onLog = { logging = row.habit },
                onSkip = { skipped -> viewModel.skip(id, skipped) },
                onClear = { viewModel.clearToday(id) },
                onPause = { viewModel.pause(id) },
                onResume = { viewModel.resume(id) },
                onEdit = { editing = row.habit },
                onArchive = { archived -> viewModel.setArchived(id, archived) },
                onDelete = { viewModel.delete(id) },
            )
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

/** The name of a group of the Habits screen. */
internal fun groupName(group: HabitGroup): Int = when (group) {
    HabitGroup.DAYS -> R.string.habits_group_days
    HabitGroup.WEEKLY -> R.string.habits_group_weekly
    HabitGroup.LIMITS -> R.string.habits_group_limits
}
