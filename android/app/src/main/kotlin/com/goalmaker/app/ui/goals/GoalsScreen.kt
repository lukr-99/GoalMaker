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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.AccountTree
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.ArrowDropDown
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.ViewAgenda
import androidx.compose.material3.AlertDialog
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.goalmaker.app.R
import com.goalmaker.app.application.planning.GoalDraft
import com.goalmaker.app.application.planning.GoalHorizon
import com.goalmaker.app.application.planning.GoalItem
import com.goalmaker.app.application.planning.GoalRules
import com.goalmaker.app.ui.components.ChoiceChip
import com.goalmaker.app.ui.components.ConfettiBurst
import com.goalmaker.app.ui.components.GoalMakerCheckbox
import com.goalmaker.app.ui.components.ProgressRing
import com.goalmaker.app.ui.components.ScreenTitle
import com.goalmaker.app.ui.lists.SectionHeader
import com.goalmaker.app.ui.theme.AppTheme
import java.text.NumberFormat
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.launch

/**
 * The Goals screen (docs/goals.md, spec stories 27 to 35): this year's, month's, week's and today's
 * goals with their rings, next week's for planning ahead, or all of them as the cascade. A tap edits a
 * goal; a goal that becomes a hit gets confetti unless motion is reduced.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GoalsScreen(viewModel: GoalsViewModel, onBack: () -> Unit) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    // null: no dialog; an empty id: a new goal.
    var editing by remember { mutableStateOf<GoalItem?>(null) }
    var logging by remember { mutableStateOf<GoalItem?>(null) }

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
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.settings_back))
                        }
                    },
                    actions = {
                        IconButton(onClick = { viewModel.showTree(!state.showTree) }) {
                            if (state.showTree) {
                                Icon(Icons.Outlined.ViewAgenda, contentDescription = stringResource(R.string.goals_show_periods))
                            } else {
                                Icon(Icons.Outlined.AccountTree, contentDescription = stringResource(R.string.goals_show_tree))
                            }
                        }
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
                    fun Card(row: GoalRow, showParent: Boolean) = GoalCard(
                        row = row,
                        showParent = showParent,
                        onEdit = { editing = row.goal },
                        onLog = { logging = row.goal },
                        onStatus = { status -> viewModel.setStatus(row.goal.id, status) },
                        onDelete = { viewModel.delete(row.goal.id) },
                    )
                    if (state.showTree) {
                        if (state.tree.isEmpty()) item(key = "empty") { Muted(stringResource(R.string.goals_tree_empty)) }
                        items(state.tree, key = { "tree-" + it.goal.id }) { row -> Card(row, showParent = false) }
                    } else {
                        state.sections.forEach { section ->
                            val id = "${section.horizon.id}-${section.start}"
                            item(key = "h-$id") { SectionHeader(sectionTitle(section)) }
                            items(section.rows, key = { it.goal.id }) { row -> Card(row, showParent = true) }
                            if (section.canCopy) {
                                item(key = "copy-$id") {
                                    TextButton(onClick = { viewModel.copyPrevious(section) }) {
                                        Icon(Icons.Outlined.ContentCopy, contentDescription = null, modifier = Modifier.size(18.dp))
                                        Text(stringResource(section.horizon.copyLabel()), modifier = Modifier.padding(start = 8.dp))
                                    }
                                }
                            }
                            item(key = "add-$id") {
                                TextButton(onClick = { editing = GoalItem("", "", section.horizon, section.start) }) {
                                    Icon(Icons.Outlined.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Text(stringResource(R.string.goals_add), modifier = Modifier.padding(start = 8.dp))
                                }
                            }
                        }
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
            onSave = { draft -> viewModel.save(goal.id.ifEmpty { null }, draft) },
            onDelete = if (goal.id.isEmpty()) null else ({ viewModel.delete(goal.id) }),
            onDismiss = { editing = null },
        )
    }
    logging?.let { goal ->
        LogDialog(goal, onLog = { amount -> viewModel.logAmount(goal.id, amount) }, onDismiss = { logging = null })
    }
}

/** A goal with its ring, where it stands and, with [showParent], the goal it serves. Indented by its depth in the cascade. */
@Composable
private fun GoalCard(
    row: GoalRow,
    showParent: Boolean,
    onEdit: () -> Unit,
    onLog: () -> Unit,
    onStatus: (String) -> Unit,
    onDelete: () -> Unit,
) {
    val goal = row.goal
    val done = goal.status == GoalRules.DONE
    val locale = LocalConfiguration.current.locales[0]
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = (row.depth * 24).dp)
            .heightIn(min = AppTheme.density.rowMinHeight.dp)
            .background(AppTheme.colors.surface, AppTheme.shapes.row)
            .clickable(onClick = onEdit)
            .padding(start = 12.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
    ) {
        ProgressRing(row.progress.fraction.toFloat(), size = 40.dp) {
            when {
                goal.emoji != null -> Text(goal.emoji, style = MaterialTheme.typography.titleMedium)
                row.progress.hit -> Icon(Icons.Outlined.Check, contentDescription = null, tint = AppTheme.colors.accent, modifier = Modifier.size(20.dp))
                goal.mode != GoalRules.MODE_DONE -> Text(
                    NumberFormat.getPercentInstance(locale).format(row.progress.fraction),
                    style = MaterialTheme.typography.labelSmall,
                )
            }
        }
        Column(Modifier.weight(1f).padding(start = 12.dp)) {
            Text(goal.title, style = MaterialTheme.typography.bodyLarge, textDecoration = if (done) TextDecoration.LineThrough else null)
            Text(progressText(row, locale), style = MaterialTheme.typography.bodySmall, color = AppTheme.colors.textMuted)
            if (showParent && row.parentTitle != null) {
                Text(stringResource(R.string.goals_serves, row.parentTitle), style = MaterialTheme.typography.bodySmall, color = AppTheme.colors.textMuted)
            }
        }
        if (goal.mode == GoalRules.MODE_NUMBER && goal.status == GoalRules.OPEN) {
            IconButton(onClick = onLog) { Icon(Icons.Outlined.Add, contentDescription = stringResource(R.string.goals_log)) }
        }
        if (goal.mode == GoalRules.MODE_DONE) {
            val label = stringResource(R.string.goals_done_box, goal.title)
            GoalMakerCheckbox(
                checked = done,
                onCheckedChange = { checked -> onStatus(if (checked) GoalRules.DONE else GoalRules.OPEN) },
                modifier = Modifier.semantics { contentDescription = label },
            )
        }
        GoalMenu(goal, onLog, onStatus, onDelete)
    }
}

@Composable
private fun GoalMenu(goal: GoalItem, onLog: () -> Unit, onStatus: (String) -> Unit, onDelete: () -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }) {
            Icon(Icons.Outlined.MoreVert, contentDescription = stringResource(R.string.goals_more, goal.title))
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
            if (goal.status == GoalRules.OPEN) {
                if (goal.mode == GoalRules.MODE_NUMBER) Choice(stringResource(R.string.goals_log), action = onLog)
                Choice(stringResource(R.string.goals_mark_done)) { onStatus(GoalRules.DONE) }
                Choice(stringResource(R.string.goals_drop)) { onStatus(GoalRules.DROPPED) }
            } else {
                Choice(stringResource(R.string.goals_reopen)) { onStatus(GoalRules.OPEN) }
            }
            Choice(stringResource(R.string.goals_delete), danger = true, action = onDelete)
        }
    }
}

/** Adds or edits a goal: its name and emoji, horizon and period, how progress is measured, and the goal it serves. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun GoalDialog(
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
                OutlinedTextField(
                    value = emoji,
                    onValueChange = { emoji = it.take(16) },
                    label = { Text(stringResource(R.string.goals_emoji)) },
                    singleLine = true,
                )
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

private fun amountText(value: Double, locale: Locale): String =
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

private fun GoalHorizon.thisLabel() = when (this) {
    GoalHorizon.YEAR -> R.string.goals_this_year
    GoalHorizon.MONTH -> R.string.goals_this_month
    GoalHorizon.WEEK -> R.string.goals_this_week
    GoalHorizon.DAY -> R.string.goals_today
}

private fun GoalHorizon.nextLabel() = when (this) {
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
