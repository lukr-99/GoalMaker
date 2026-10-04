package com.goalmaker.app.ui.goals

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.EventNote
import androidx.compose.material.icons.automirrored.outlined.ViewList
import androidx.compose.material.icons.outlined.AccountTree
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.ArrowDropDown
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.TouchApp
import androidx.compose.material.icons.outlined.ViewAgenda
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.goalmaker.app.R
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.text.input.clearText
import androidx.compose.foundation.text.input.rememberTextFieldState
import com.goalmaker.app.ui.chat.ChatViewModel
import com.goalmaker.app.ui.composer.BottomComposer
import com.goalmaker.app.ui.composer.LineOutcome
import com.goalmaker.app.application.planning.GoalDraft
import com.goalmaker.app.application.planning.GoalHorizon
import com.goalmaker.app.application.planning.GoalItem
import com.goalmaker.app.application.planning.GoalRules
import com.goalmaker.app.domain.settings.GoalsView
import com.goalmaker.app.ui.components.ChoiceChip
import com.goalmaker.app.ui.components.ConfettiBurst
import com.goalmaker.app.ui.components.EmojiField
import com.goalmaker.app.ui.components.ScreenTitle
import com.goalmaker.app.ui.lists.SectionHeader
import com.goalmaker.app.ui.nav.PlaceNavigationIcon
import com.goalmaker.app.ui.theme.AppTheme
import java.text.NumberFormat
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.launch

/**
 * The Goals screen (docs/goals.md, spec stories 27 to 35): the horizon rings on top, then the ladder from
 * this year down to today with each goal as a card, then next week for planning ahead. A ring shows only
 * its rung; a tap on a card lights what it feeds and what feeds it. A goal that becomes a hit gets
 * confetti unless motion is reduced. The top bar switches to the plain list, one compact row per goal
 * grouped by period, where a tap opens the goal; this phone remembers the pick. The bottom bar
 * (docs/composer.md) adds a goal from a typed line, or opens the form filled in when no title is
 * left; its plus opens the empty form for this week, and its switch turns it into the quick [chat].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GoalsScreen(viewModel: GoalsViewModel, chat: ChatViewModel, onBack: (() -> Unit)?, actions: @Composable () -> Unit = {}) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    // null: no dialog; an empty id: a new goal.
    var editing by remember { mutableStateOf<GoalItem?>(null) }
    var logging by remember { mutableStateOf<GoalItem?>(null) }
    // The bottom bar's line, and whether the open form came from it (it empties once the form saves).
    val composer = rememberTextFieldState()
    var fromLine by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    fun open(goal: GoalItem) {
        fromLine = false
        editing = goal
    }

    // Confetti when a shown goal becomes a hit while the screen is open.
    val reduceMotion = AppTheme.reduceMotion
    var seenHits by remember { mutableStateOf<Set<String>?>(null) }
    var bursts by remember { mutableIntStateOf(0) }
    LaunchedEffect(state.loaded, state.hits) {
        if (!state.loaded) return@LaunchedEffect
        val before = seenHits
        seenHits = state.hits
        if (before != null && !reduceMotion && !before.containsAll(state.hits)) bursts++
    }

    Box(Modifier.fillMaxSize()) {
        Scaffold(
            modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
            topBar = {
                MediumFlexibleTopAppBar(
                    title = { ScreenTitle(stringResource(R.string.goals_title)) },
                    navigationIcon = { PlaceNavigationIcon(onBack) },
                    actions = {
                        ViewSwitch(state.view, onPick = viewModel::showView)
                        actions()
                    },
                    scrollBehavior = scrollBehavior,
                )
            },
            bottomBar = {
                // Under MainScreen's bar the navigation bar is already taken, so this adds nothing there.
                val line = composer.text.toString()
                val draft = remember(line, state.today) { viewModel.preview(line) }
                BottomComposer(
                    state = composer,
                    chat = chat,
                    chips = goalLineChips(line, draft, viewModel.today()),
                    canAdd = line.isNotBlank(),
                    onAdd = {
                        if (line.isNotBlank()) scope.launch {
                            when (val outcome = viewModel.addLine(line)) {
                                LineOutcome.Added -> composer.clearText()
                                is LineOutcome.OpenForm -> {
                                    fromLine = true
                                    editing = outcome.prefill.asNewGoal()
                                }
                            }
                        }
                    },
                    placeholder = stringResource(R.string.bar_goal_placeholder),
                    addLabel = stringResource(R.string.bar_add_goal),
                    formLabel = stringResource(R.string.bar_new_goal),
                    onOpenForm = { open(GoalItem("", "", GoalHorizon.WEEK, GoalRules.periodStart(GoalHorizon.WEEK, viewModel.today()))) },
                    modifier = Modifier.navigationBarsPadding().imePadding(),
                )
            },
        ) { padding ->
            if (state.loaded && state.view == GoalsView.LIST) {
                // The plain list: each period's goals as compact rows, no rings, rail or chain.
                LazyColumn(
                    contentPadding = PaddingValues(horizontal = AppTheme.density.pagePadding.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(AppTheme.density.rowGap.dp),
                    modifier = Modifier.fillMaxSize().padding(padding),
                ) {
                    state.groups.forEach { section ->
                        val id = "${section.horizon.id}-${section.start}-${section.next}"
                        item(key = "lh-$id") {
                            ListGroupHeader(section, onAdd = { open(GoalItem("", "", section.horizon, section.start)) })
                        }
                        items(section.rows, key = { "l-" + it.goal.id }) { row ->
                            GoalListRow(
                                row = row,
                                onOpen = { editing = row.goal },
                                onQuickLog = { if (!viewModel.quickLog(row)) logging = row.goal },
                                onStatus = { status -> viewModel.setStatus(row.goal.id, status) },
                            )
                        }
                        if (section.rows.isEmpty()) {
                            item(key = "le-$id") { Muted(stringResource(R.string.goals_list_empty)) }
                        }
                    }
                }
            } else if (state.loaded) {
                LazyColumn(
                    contentPadding = PaddingValues(horizontal = AppTheme.density.pagePadding.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(AppTheme.density.rowGap.dp),
                    modifier = Modifier.fillMaxSize().padding(padding),
                ) {
                    item(key = "rings") { HorizonRings(state.rings, state.filter, onFilter = viewModel::filter) }
                    item(key = "hint") { ChainHint(state, onClear = viewModel::clearPick) }
                    state.rungs.forEach { section ->
                        rung(
                            section = section,
                            state = state,
                            card = { row ->
                                GoalCard(
                                    row = row,
                                    lit = if (state.chain.isEmpty()) null else row.goal.id in state.chain,
                                    picked = state.picked?.goal?.id == row.goal.id,
                                    onPick = { viewModel.pick(row.goal.id) },
                                    onQuickLog = { if (!viewModel.quickLog(row)) logging = row.goal },
                                    onLog = { logging = row.goal },
                                    onEdit = { editing = row.goal },
                                    onStatus = { status -> viewModel.setStatus(row.goal.id, status) },
                                    onDelete = { viewModel.delete(row.goal.id) },
                                )
                            },
                            onAdd = { open(GoalItem("", "", section.horizon, section.start)) },
                            onCopy = { viewModel.copyPrevious(section) },
                        )
                    }
                    state.nextWeek?.let { next ->
                        item(key = "h-next") { SectionHeader(sectionTitle(next)) }
                        items(next.rows, key = { "next-" + it.goal.id }) { row -> GoalSummaryRow(row, onClick = { editing = row.goal }) }
                        if (next.rows.isEmpty()) {
                            item(key = "copy-next") { NextWeekEmpty(next.canCopy, onCopy = { viewModel.copyPrevious(next) }) }
                        }
                        item(key = "add-next") { AddGoal(onClick = { open(GoalItem("", "", next.horizon, next.start)) }) }
                    }
                }
            }
        }
        if (bursts > 0) {
            key(bursts) { ConfettiBurst(onFinished = { bursts = 0 }) }
        }
    }

    editing?.let { goal ->
        GoalDialog(
            goal = goal,
            today = state.today,
            goals = state.goals,
            onSave = { draft ->
                viewModel.save(goal.id.ifEmpty { null }, draft).also { saved -> if (saved && fromLine) composer.clearText() }
            },
            onDelete = if (goal.id.isEmpty()) null else ({ viewModel.delete(goal.id) }),
            onDismiss = {
                editing = null
                fromLine = false
            },
        )
    }
    logging?.let { goal ->
        LogDialog(goal, onLog = { amount -> viewModel.logAmount(goal.id, amount) }, onDismiss = { logging = null })
    }
}

// One rung of the ladder: its badge, period and how it stands, its cards on the rail, then Add a goal
// (and Copy last period's goals while it is empty).
private fun LazyListScope.rung(
    section: GoalSection,
    state: GoalsUiState,
    card: @Composable (GoalRow) -> Unit,
    onAdd: () -> Unit,
    onCopy: () -> Unit,
) {
    val id = "${section.horizon.id}-${section.start}"
    item(key = "h-$id") { RungHeader(section, state.today) }
    items(section.rows, key = { it.goal.id }) { row -> Box(Modifier.rail(tick = true)) { card(row) } }
    item(key = "add-$id") {
        Row(Modifier.rail(tick = false), verticalAlignment = Alignment.CenterVertically) {
            AddGoal(onClick = onAdd)
            if (section.canCopy) {
                TextButton(onClick = onCopy) {
                    Icon(Icons.Outlined.ContentCopy, contentDescription = null, modifier = Modifier.size(18.dp))
                    Text(stringResource(section.horizon.copyLabel()), modifier = Modifier.padding(start = 8.dp))
                }
            }
        }
    }
}

// The ladder's rail: a line down the left and a short tick to each card, indented past the badge.
@Composable
private fun Modifier.rail(tick: Boolean): Modifier {
    val color = AppTheme.colors.outline.copy(alpha = 0.35f)
    val gap = AppTheme.density.rowGap.dp
    return drawBehind {
        val x = RAIL_X.dp.toPx()
        val width = 2.dp.toPx()
        drawLine(color, Offset(x, -gap.toPx()), Offset(x, size.height), strokeWidth = width)
        if (tick) drawLine(color, Offset(x, TICK_Y.dp.toPx()), Offset(RAIL_INDENT.dp.toPx() - 6.dp.toPx(), TICK_Y.dp.toPx()), strokeWidth = width)
    }.padding(start = RAIL_INDENT.dp)
}

@Composable
private fun RungHeader(section: GoalSection, today: LocalDate) {
    val locale = LocalConfiguration.current.locales[0]
    val line = stringResource(R.string.goals_rung_line, stringResource(R.string.goals_ring_hit, section.hits, section.rows.size), goneText(section, today, locale))
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 12.dp)
            .heightIn(min = 40.dp)
            .semantics(mergeDescendants = true) { heading() },
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.sizeIn(minWidth = 24.dp, minHeight = 24.dp).background(AppTheme.colors.accent, RoundedCornerShape(8.dp)),
        ) {
            Text(stringResource(section.horizon.badge()), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Black, color = AppTheme.colors.onAccent)
        }
        Column(Modifier.padding(start = RAIL_INDENT.dp - 24.dp)) {
            Text(rungTitle(section, locale), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(line, style = MaterialTheme.typography.bodySmall, color = AppTheme.colors.textMuted)
        }
    }
}

// Under the rings: how to light a chain and how many goals need you, or the lit chain with Clear.
@Composable
private fun ChainHint(state: GoalsUiState, onClear: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
        val picked = state.picked
        Icon(
            if (picked != null) Icons.Outlined.AccountTree else Icons.Outlined.TouchApp,
            contentDescription = null,
            tint = AppTheme.colors.textMuted,
            modifier = Modifier.size(20.dp),
        )
        val text = if (picked != null) {
            stringResource(R.string.goals_chain_of, picked.goal.title)
        } else {
            val hint = stringResource(R.string.goals_hint)
            if (state.behind > 0) hint + " " + pluralStringResource(R.plurals.goals_need_you, state.behind, state.behind) else hint
        }
        Text(
            text,
            style = MaterialTheme.typography.bodyMedium,
            color = if (picked != null) AppTheme.colors.text else AppTheme.colors.textMuted,
            modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
        )
        if (picked != null) {
            AssistChip(
                onClick = onClear,
                label = { Text(stringResource(R.string.goals_chain_clear)) },
                leadingIcon = { Icon(Icons.Outlined.Close, contentDescription = null, modifier = Modifier.size(AssistChipDefaults.IconSize)) },
            )
        }
    }
}

// Next week has no goals yet: say so, and offer this week's when there are some to copy.
@Composable
private fun NextWeekEmpty(canCopy: Boolean, onCopy: () -> Unit) {
    val outline = AppTheme.colors.outline.copy(alpha = 0.7f)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .drawBehind {
                drawRoundRect(
                    outline,
                    cornerRadius = CornerRadius(12.dp.toPx()),
                    style = Stroke(width = 1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 4.dp.toPx()))),
                )
            }
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .heightIn(min = 48.dp),
    ) {
        Icon(Icons.AutoMirrored.Outlined.EventNote, contentDescription = null, tint = AppTheme.colors.textMuted, modifier = Modifier.size(20.dp))
        Text(
            stringResource(R.string.goals_next_empty),
            style = MaterialTheme.typography.bodyMedium,
            color = AppTheme.colors.textMuted,
            modifier = Modifier.weight(1f).padding(horizontal = 10.dp),
        )
        if (canCopy) {
            AssistChip(
                onClick = onCopy,
                label = { Text(stringResource(R.string.goals_copy_this_week)) },
                leadingIcon = { Icon(Icons.Outlined.ContentCopy, contentDescription = null, modifier = Modifier.size(AssistChipDefaults.IconSize)) },
                colors = AssistChipDefaults.assistChipColors(containerColor = AppTheme.colors.accent.copy(alpha = 0.18f)),
            )
        }
    }
}

/** Switches between the ladder and the plain list; the icon shows where it leads. */
@Composable
private fun ViewSwitch(view: GoalsView, onPick: (GoalsView) -> Unit) {
    val toList = view == GoalsView.LADDER
    IconButton(onClick = { onPick(if (toList) GoalsView.LIST else GoalsView.LADDER) }) {
        Icon(
            if (toList) Icons.AutoMirrored.Outlined.ViewList else Icons.Outlined.ViewAgenda,
            contentDescription = stringResource(if (toList) R.string.goals_view_list else R.string.goals_view_ladder),
        )
    }
}

// A group of the plain list: the period's name and dates, how many of its goals are hit, and Add.
@Composable
private fun ListGroupHeader(section: GoalSection, onAdd: () -> Unit) {
    val title = sectionTitle(section)
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(1f).semantics(mergeDescendants = true) { heading() },
        ) {
            Text(
                AppTheme.headline(title).uppercase(LocalConfiguration.current.locales[0]),
                style = MaterialTheme.typography.labelMedium,
                color = AppTheme.colors.accent,
                modifier = Modifier.padding(start = 4.dp).weight(1f, fill = false),
            )
            if (section.rows.isNotEmpty()) {
                Text(
                    stringResource(R.string.goals_ring_hit, section.hits, section.rows.size),
                    style = MaterialTheme.typography.labelMedium,
                    color = AppTheme.colors.textMuted,
                    modifier = Modifier.padding(start = 8.dp),
                )
            }
        }
        IconButton(onClick = onAdd) {
            Icon(Icons.Outlined.Add, contentDescription = stringResource(R.string.goals_add_to, title), tint = AppTheme.colors.accent)
        }
    }
}

@Composable
private fun AddGoal(onClick: () -> Unit) {
    TextButton(onClick = onClick) {
        Icon(Icons.Outlined.Add, contentDescription = null, modifier = Modifier.size(18.dp))
        Text(stringResource(R.string.goals_add), modifier = Modifier.padding(start = 8.dp))
    }
}

/** Adds or edits a goal: its name and emoji, horizon and period, how progress is measured, and the goal it serves. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun GoalDialog(
    goal: GoalItem,
    today: LocalDate,
    goals: List<GoalItem>,
    onSave: suspend (GoalDraft) -> Boolean,
    onDelete: (() -> Unit)?,
    onDismiss: () -> Unit,
) {
    var title by remember { mutableStateOf(goal.title) }
    var emoji by remember { mutableStateOf(goal.emoji.orEmpty()) }
    var horizon by remember { mutableStateOf(goal.horizon) }
    var start by remember { mutableStateOf(goal.periodStart) }
    var mode by remember { mutableStateOf(goal.mode) }
    var target by remember { mutableStateOf(goal.target?.let(::plainAmount).orEmpty()) }
    var unit by remember { mutableStateOf(goal.unit.orEmpty()) }
    var parentId by remember { mutableStateOf(goal.parentId) }
    var refused by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val locale = LocalConfiguration.current.locales[0]

    val current = GoalRules.periodStart(horizon, today)
    val next = GoalRules.periodEnd(horizon, current).plusDays(1)
    val periods = listOfNotNull(current, next, start.takeIf { it != current && it != next })
    // The goals this one can serve: a longer horizon whose period overlaps (docs/goals.md).
    val parents = goals.filter { it.id != goal.id && it.status != GoalRules.DROPPED && GoalRules.canServe(horizon, start, it.horizon, it.periodStart) }
    val parent = parents.firstOrNull { it.id == parentId }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(if (goal.id.isEmpty()) R.string.goals_new else R.string.goals_edit)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.verticalScroll(rememberScrollState())) {
                OutlinedTextField(
                    value = title,
                    onValueChange = {
                        title = it
                        refused = false
                    },
                    label = { Text(stringResource(R.string.goals_name)) },
                    isError = refused && title.isBlank(),
                    singleLine = true,
                )
                EmojiField(emoji, onChange = { emoji = it })
                Label(stringResource(R.string.goals_horizon))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    GoalHorizon.entries.forEach { choice ->
                        ChoiceChip(
                            selected = horizon == choice,
                            onClick = {
                                if (horizon != choice) {
                                    horizon = choice
                                    start = GoalRules.periodStart(choice, today)
                                }
                            },
                            label = stringResource(choice.label()),
                        )
                    }
                }
                Label(stringResource(R.string.goals_period))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    periods.forEach { choice ->
                        ChoiceChip(
                            selected = start == choice,
                            onClick = { start = choice },
                            label = when (choice) {
                                current -> stringResource(horizon.thisLabel())
                                next -> stringResource(horizon.nextLabel())
                                else -> periodText(horizon, choice, locale)
                            },
                        )
                    }
                }
                Label(stringResource(R.string.goals_progress))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(
                        GoalRules.MODE_DONE to R.string.goals_mode_done,
                        GoalRules.MODE_TASKS to R.string.goals_mode_tasks,
                        GoalRules.MODE_NUMBER to R.string.goals_mode_number,
                    ).forEach { (id, label) ->
                        ChoiceChip(
                            selected = mode == id,
                            onClick = {
                                mode = id
                                refused = false
                            },
                            label = stringResource(label),
                        )
                    }
                }
                if (mode == GoalRules.MODE_NUMBER) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = target,
                            onValueChange = {
                                target = it
                                refused = false
                            },
                            label = { Text(stringResource(R.string.goals_target)) },
                            isError = refused && (parseAmount(target) ?: 0.0) <= 0.0,
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            modifier = Modifier.weight(1f),
                        )
                        OutlinedTextField(
                            value = unit,
                            onValueChange = { unit = it.take(20) },
                            label = { Text(stringResource(R.string.goals_unit)) },
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
                if (parents.isNotEmpty()) ParentField(parent, parents, onPick = { parentId = it })
                if (refused) {
                    Text(stringResource(R.string.goals_invalid), style = MaterialTheme.typography.bodySmall, color = AppTheme.colors.danger)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val draft = GoalDraft(
                    title = title,
                    horizon = horizon,
                    periodStart = start,
                    mode = mode,
                    emoji = emoji,
                    parentId = parent?.id,
                    target = parseAmount(target),
                    unit = unit,
                )
                scope.launch { if (onSave(draft)) onDismiss() else refused = true }
            }) { Text(stringResource(R.string.goals_save)) }
        },
        dismissButton = {
            Row {
                onDelete?.let { delete ->
                    TextButton(onClick = {
                        delete()
                        onDismiss()
                    }) { Text(stringResource(R.string.goals_delete), color = AppTheme.colors.danger) }
                }
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.goals_cancel)) }
            }
        },
    )
}

@Composable
private fun ParentField(parent: GoalItem?, parents: List<GoalItem>, onPick: (String?) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Column {
        Label(stringResource(R.string.goals_parent))
        Box {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .clickable { open = true },
            ) {
                Text(parent?.let(::goalName) ?: stringResource(R.string.goals_no_parent), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                Icon(Icons.Outlined.ArrowDropDown, contentDescription = null)
            }
            DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                DropdownMenuItem(text = { Text(stringResource(R.string.goals_no_parent)) }, onClick = {
                    open = false
                    onPick(null)
                })
                parents.forEach { choice ->
                    DropdownMenuItem(text = { Text(goalName(choice)) }, onClick = {
                        open = false
                        onPick(choice.id)
                    })
                }
            }
        }
    }
}

/** Logs an amount on a numeric goal; Take off logs it negative, to correct a mistake. */
@Composable
private fun LogDialog(goal: GoalItem, onLog: (Double) -> Unit, onDismiss: () -> Unit) {
    var text by remember { mutableStateOf("") }
    val amount = parseAmount(text)?.takeIf { it > 0.0 }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.goals_log_title, goal.title)) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                label = { Text(stringResource(R.string.goals_log_amount)) },
                suffix = goal.unit?.let { unit -> { Text(unit) } },
                supportingText = { Text(stringResource(R.string.goals_log_hint)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.focusRequester(focus),
            )
        },
        confirmButton = {
            Row {
                TextButton(enabled = amount != null, onClick = {
                    amount?.let { onLog(-it) }
                    onDismiss()
                }) { Text(stringResource(R.string.goals_log_take_off)) }
                TextButton(enabled = amount != null, onClick = {
                    amount?.let(onLog)
                    onDismiss()
                }) { Text(stringResource(R.string.goals_log_add)) }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.goals_cancel)) } },
    )
}

@Composable
private fun Label(text: String) {
    Text(text, style = MaterialTheme.typography.titleSmall, color = AppTheme.colors.accent)
}

@Composable
private fun Muted(text: String) {
    Text(text, style = MaterialTheme.typography.bodyLarge, color = AppTheme.colors.textMuted, modifier = Modifier.padding(8.dp))
}

/** "This week · 14 to 20 Sep", the heading of a period's goals. */
@Composable
private fun sectionTitle(section: GoalSection): String {
    val locale = LocalConfiguration.current.locales[0]
    val name = stringResource(if (section.next) section.horizon.nextLabel() else section.horizon.thisLabel())
    return stringResource(R.string.goals_section, name, periodText(section.horizon, section.start, locale))
}

/** Where a goal stands in words: "2 of 5 tasks done", "12 of 50 km", "Done". */
@Composable
internal fun progressText(row: GoalRow, locale: Locale): String {
    val goal = row.goal
    val progress = row.progress
    return when (goal.mode) {
        GoalRules.MODE_TASKS -> if (progress.target <= 0.0) {
            stringResource(R.string.goals_no_tasks)
        } else {
            val total = progress.target.toInt()
            pluralStringResource(R.plurals.goals_tasks_done, total, progress.value.toInt(), total)
        }
        GoalRules.MODE_NUMBER -> {
            val value = amountText(progress.value, locale)
            val target = amountText(progress.target, locale)
            goal.unit?.let { stringResource(R.string.goals_amount_unit, value, target, it) } ?: stringResource(R.string.goals_amount, value, target)
        }
        else -> stringResource(if (goal.status == GoalRules.DONE) R.string.goals_state_done else R.string.goals_state_open)
    }
}

/** A period by its dates: "2026", "September 2026", "14 to 20 Sep", "Saturday 19 September". */
@Composable
private fun periodText(horizon: GoalHorizon, start: LocalDate, locale: Locale): String = when (horizon) {
    GoalHorizon.YEAR -> start.year.toString()
    GoalHorizon.MONTH -> DateTimeFormatter.ofPattern("LLLL yyyy", locale).format(start)
    GoalHorizon.WEEK -> {
        val end = start.plusDays(6)
        val first = DateTimeFormatter.ofPattern(if (start.month == end.month) "d" else "d MMM", locale).format(start)
        stringResource(R.string.goals_range, first, DateTimeFormatter.ofPattern("d MMM", locale).format(end))
    }
    GoalHorizon.DAY -> DateTimeFormatter.ofPattern("EEEE d MMMM", locale).format(start)
}

private fun goalName(goal: GoalItem) = listOfNotNull(goal.emoji, goal.title).joinToString(" ")

internal fun amountText(value: Double, locale: Locale): String =
    NumberFormat.getNumberInstance(locale).apply { maximumFractionDigits = 2 }.format(value)

// A target as it was typed: no ".0" on whole numbers.
private fun plainAmount(value: Double): String = if (value % 1.0 == 0.0) value.toLong().toString() else value.toString()

// "5", "5.5" or "5,5"; null when it isn't a number.
private fun parseAmount(text: String): Double? = text.trim().replace(',', '.').toDoubleOrNull()?.takeIf(Double::isFinite)

private fun GoalHorizon.label() = when (this) {
    GoalHorizon.YEAR -> R.string.goals_horizon_year
    GoalHorizon.MONTH -> R.string.goals_horizon_month
    GoalHorizon.WEEK -> R.string.goals_horizon_week
    GoalHorizon.DAY -> R.string.goals_horizon_day
}

internal fun GoalHorizon.thisLabel() = when (this) {
    GoalHorizon.YEAR -> R.string.goals_this_year
    GoalHorizon.MONTH -> R.string.goals_this_month
    GoalHorizon.WEEK -> R.string.goals_this_week
    GoalHorizon.DAY -> R.string.goals_today
}

internal fun GoalHorizon.nextLabel() = when (this) {
    GoalHorizon.YEAR -> R.string.goals_next_year
    GoalHorizon.MONTH -> R.string.goals_next_month
    GoalHorizon.WEEK -> R.string.goals_next_week
    GoalHorizon.DAY -> R.string.goals_tomorrow
}

private fun GoalHorizon.copyLabel() = when (this) {
    GoalHorizon.YEAR -> R.string.goals_copy_year
    GoalHorizon.MONTH -> R.string.goals_copy_month
    else -> R.string.goals_copy_week
}

/** A rung's title: "2026", "October", "28 Sep to 4 Oct", "Thursday 1 October". */
@Composable
private fun rungTitle(section: GoalSection, locale: Locale): String = when (section.horizon) {
    GoalHorizon.MONTH -> DateTimeFormatter.ofPattern("LLLL", locale).format(section.start)
    else -> periodText(section.horizon, section.start, locale)
}

/** How much of a rung's period is gone: "75% of the year gone", "Day 4 of 7", "Today". */
@Composable
private fun goneText(section: GoalSection, today: LocalDate, locale: Locale): String {
    val length = (GoalRules.periodEnd(section.horizon, section.start).toEpochDay() - section.start.toEpochDay() + 1).toInt()
    val day = (today.toEpochDay() - section.start.toEpochDay() + 1).toInt().coerceIn(1, length)
    return when (section.horizon) {
        GoalHorizon.YEAR -> stringResource(
            R.string.goals_year_gone,
            NumberFormat.getPercentInstance(locale).format(GoalRules.elapsed(section.horizon, section.start, today)),
        )
        GoalHorizon.DAY -> stringResource(R.string.goals_today)
        else -> stringResource(R.string.goals_day_of, day, length)
    }
}

private fun GoalHorizon.badge() = when (this) {
    GoalHorizon.YEAR -> R.string.goals_badge_year
    GoalHorizon.MONTH -> R.string.goals_badge_month
    GoalHorizon.WEEK -> R.string.goals_badge_week
    GoalHorizon.DAY -> R.string.goals_badge_day
}

// Where the ladder's rail runs, where each card's tick meets it, and how far the cards sit in, in dp.
private const val RAIL_X = 11
private const val TICK_Y = 28
private const val RAIL_INDENT = 32
