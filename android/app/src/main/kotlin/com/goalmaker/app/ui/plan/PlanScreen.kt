package com.goalmaker.app.ui.plan

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.input.clearText
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.DoNotDisturbOn
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material.icons.outlined.WbTwilight
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.FilledIconToggleButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MediumFlexibleTopAppBar
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.goalmaker.app.R
import com.goalmaker.app.application.planning.AreaItem
import com.goalmaker.app.application.planning.PlanDecision
import com.goalmaker.app.application.planning.PlanRules
import com.goalmaker.app.application.planning.TaskItem
import com.goalmaker.app.ui.components.rememberTickSound
import com.goalmaker.app.ui.composer.ComposerBar
import com.goalmaker.app.ui.composer.composerChips
import com.goalmaker.app.ui.composer.removeParts
import com.goalmaker.app.ui.lists.SectionHeader
import com.goalmaker.app.ui.lists.TaskDetails
import com.goalmaker.app.ui.lists.TaskTime
import com.goalmaker.app.ui.theme.AppTheme
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/**
 * The Plan tomorrow ritual (docs/plan-tomorrow.md): step 1 decides on what's left from today, step 2
 * sets up tomorrow and its top priorities, then a summary. Back from step 2 returns to step 1.
 */
@Composable
fun PlanScreen(viewModel: PlanViewModel, onClose: () -> Unit) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val composer = rememberTextFieldState()
    val tick = rememberTickSound()
    val sound = AppTheme.completionSound
    val haptics = LocalHapticFeedback.current
    var picking by remember { mutableStateOf<TaskItem?>(null) }

    BackHandler(enabled = state.step == PlanStep.TOMORROW) { viewModel.back() }

    Scaffold(
        topBar = {
            MediumFlexibleTopAppBar(
                title = { Text(AppTheme.headline(stringResource(R.string.plan_title))) },
                subtitle = { Text(subtitle(state)) },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(Icons.Outlined.Close, contentDescription = stringResource(R.string.plan_close))
                    }
                },
                actions = {
                    when (state.step) {
                        PlanStep.TODAY -> TextButton(onClick = viewModel::next, enabled = state.loaded && state.undecided == 0) {
                            Text(stringResource(R.string.plan_next))
                        }
                        PlanStep.TOMORROW -> TextButton(onClick = viewModel::next) { Text(stringResource(R.string.plan_finish)) }
                        PlanStep.DONE -> Unit
                    }
                },
            )
        },
        bottomBar = {
            if (state.step == PlanStep.TOMORROW) {
                val line = composer.text.toString()
                val draft = remember(line) { viewModel.preview(line) }
                ComposerBar(
                    state = composer,
                    chips = composerChips(line, draft, state.today, state.areas, state.tagNames),
                    canSend = (draft.title.isNotBlank() && draft.command == null) || draft.command?.name == PlanRules.COMMAND,
                    onSubmit = { if (viewModel.submit(draft)) composer.clearText() },
                    onRemove = { chip -> composer.setTextAndPlaceCursorAtEnd(removeParts(line, chip.spans)) },
                    modifier = Modifier.navigationBarsPadding().imePadding(),
                )
            }
        },
    ) { padding ->
        val areaById = state.areas.associateBy(AreaItem::id)
        Box(Modifier.fillMaxSize().padding(padding)) {
            when (state.step) {
                PlanStep.TODAY -> TodayStep(state, areaById) { item, decision ->
                    when (decision) {
                        PlanDecision.LATER -> picking = item.task
                        else -> {
                            haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                            if (decision == PlanDecision.DONE && item.decision != PlanDecision.DONE && sound) tick()
                            viewModel.decide(item.task, decision)
                        }
                    }
                }
                PlanStep.TOMORROW -> TomorrowStep(state, areaById, viewModel::togglePriority, viewModel::planForTomorrow)
                PlanStep.DONE -> DoneStep(state, onClose)
            }
        }
    }

    picking?.let { task ->
        LaterPicker(
            today = state.today,
            current = task.plannedDate?.takeIf { it.isAfter(state.today.plusDays(1)) },
            onPick = { day ->
                picking = null
                viewModel.decide(task, PlanDecision.LATER, day)
            },
            onDismiss = { picking = null },
        )
    }
}

@Composable
private fun subtitle(state: PlanUiState): String = when (state.step) {
    PlanStep.TODAY ->
        if (state.undecided == 0) {
            stringResource(R.string.plan_step_today_done)
        } else {
            pluralStringResource(R.plurals.plan_step_today, state.undecided, state.undecided)
        }
    PlanStep.TOMORROW -> pluralStringResource(R.plurals.plan_step_tomorrow, state.priorities, state.priorities, PlanRules.MAX_PRIORITIES)
    PlanStep.DONE -> stringResource(R.string.plan_step_done)
}

@Composable
private fun TodayStep(state: PlanUiState, areaById: Map<String, AreaItem>, onDecide: (ReviewItem, PlanDecision) -> Unit) {
    LazyColumn(
        contentPadding = PaddingValues(horizontal = AppTheme.density.pagePadding.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(AppTheme.density.rowGap.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        item(key = "intro") { Intro(stringResource(if (state.review.isEmpty()) R.string.plan_today_clear else R.string.plan_today_intro)) }
        items(state.review, key = { it.task.id }) { item ->
            ReviewCard(item, state.today, item.task.areaId?.let(areaById::get), { decision -> onDecide(item, decision) }, Modifier.animateItem())
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ReviewCard(item: ReviewItem, today: LocalDate, area: AreaItem?, onDecide: (PlanDecision) -> Unit, modifier: Modifier = Modifier) {
    val task = item.task
    val locale = LocalConfiguration.current.locales[0]
    Surface(color = AppTheme.colors.surface, shape = AppTheme.shapes.row, modifier = modifier.fillMaxWidth()) {
        Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(task.title, style = MaterialTheme.typography.bodyLarge)
                    TaskDetails(task, area, showDay = task.plannedDate?.isBefore(today) == true)
                }
                task.plannedTime?.let { TaskTime(it) }
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
                Decision(item.decision == PlanDecision.TOMORROW, stringResource(R.string.plan_decide_tomorrow), Icons.Outlined.WbTwilight) {
                    onDecide(PlanDecision.TOMORROW)
                }
                val later = item.decision == PlanDecision.LATER
                Decision(
                    later,
                    if (later) DateTimeFormatter.ofPattern("EEE d MMM", locale).format(task.plannedDate) else stringResource(R.string.plan_decide_date),
                    Icons.Outlined.CalendarMonth,
                ) { onDecide(PlanDecision.LATER) }
                Decision(item.decision == PlanDecision.DONE, stringResource(R.string.plan_decide_done), Icons.Outlined.Check) {
                    onDecide(PlanDecision.DONE)
                }
                Decision(item.decision == PlanDecision.DROPPED, stringResource(R.string.plan_decide_drop), Icons.Outlined.DoNotDisturbOn) {
                    onDecide(PlanDecision.DROPPED)
                }
            }
            if (item.decision == PlanDecision.UNPLANNED) {
                Text(stringResource(R.string.plan_unplanned), style = MaterialTheme.typography.bodySmall, color = AppTheme.colors.textMuted)
            }
        }
    }
}

@Composable
private fun Decision(selected: Boolean, label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label) },
        leadingIcon = { Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp)) },
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = AppTheme.colors.accent,
            selectedLabelColor = AppTheme.colors.onAccent,
            selectedLeadingIconColor = AppTheme.colors.onAccent,
        ),
    )
}

@Composable
private fun TomorrowStep(
    state: PlanUiState,
    areaById: Map<String, AreaItem>,
    onTogglePriority: (TaskItem) -> Unit,
    onPlanForTomorrow: (TaskItem) -> Unit,
) {
    var inboxOpen by rememberSaveable { mutableStateOf(false) }
    LazyColumn(
        contentPadding = PaddingValues(horizontal = AppTheme.density.pagePadding.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(AppTheme.density.rowGap.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        item(key = "intro") {
            Intro(
                if (state.tomorrow.isEmpty()) {
                    stringResource(R.string.plan_tomorrow_empty)
                } else {
                    pluralStringResource(R.plurals.plan_tomorrow_intro, PlanRules.MAX_PRIORITIES, PlanRules.MAX_PRIORITIES)
                },
            )
        }
        items(state.tomorrow, key = { it.id }) { task ->
            TaskLine(task, task.areaId?.let(areaById::get), Modifier.animateItem()) {
                val label = stringResource(R.string.composer_priority)
                FilledIconToggleButton(
                    checked = task.topPriority,
                    onCheckedChange = { onTogglePriority(task) },
                    enabled = task.topPriority || state.canPickPriority,
                    modifier = Modifier.semantics { contentDescription = label },
                ) {
                    Icon(if (task.topPriority) Icons.Filled.Flag else Icons.Outlined.Flag, contentDescription = null)
                }
            }
        }
        if (state.inbox.isNotEmpty()) {
            item(key = "h-inbox") {
                SectionHeader(
                    text = pluralStringResource(R.plurals.plan_from_inbox, state.inbox.size, state.inbox.size),
                    expanded = inboxOpen,
                    onToggle = { inboxOpen = !inboxOpen },
                )
            }
            if (inboxOpen) {
                items(state.inbox, key = { "inbox-" + it.id }) { task ->
                    TaskLine(task, null, Modifier.animateItem()) {
                        val label = stringResource(R.string.plan_to_tomorrow, task.title)
                        TextButton(onClick = { onPlanForTomorrow(task) }, modifier = Modifier.semantics { contentDescription = label }) {
                            Text(stringResource(R.string.plan_decide_tomorrow))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TaskLine(task: TaskItem, area: AreaItem?, modifier: Modifier = Modifier, trailing: @Composable () -> Unit) {
    Surface(color = AppTheme.colors.surface, shape = AppTheme.shapes.row, modifier = modifier.fillMaxWidth()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .heightIn(min = AppTheme.density.rowMinHeight.dp)
                .padding(start = 16.dp, end = 8.dp),
        ) {
            Column(Modifier.weight(1f).padding(vertical = 10.dp)) {
                Text(task.title, style = MaterialTheme.typography.bodyLarge)
                TaskDetails(task, area, showDay = false, showPriority = false)
            }
            task.plannedTime?.let { TaskTime(it) }
            trailing()
        }
    }
}

@Composable
private fun DoneStep(state: PlanUiState, onClose: () -> Unit) {
    val reduceMotion = AppTheme.reduceMotion
    val haptics = LocalHapticFeedback.current
    val scale = remember { Animatable(if (reduceMotion) 1f else 0.9f) }
    LaunchedEffect(Unit) {
        haptics.performHapticFeedback(HapticFeedbackType.Confirm)
        if (!reduceMotion) scale.animateTo(1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy))
    }
    Column(
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = AppTheme.density.pagePadding.dp, vertical = 16.dp),
    ) {
        Surface(
            color = AppTheme.colors.hero,
            contentColor = AppTheme.colors.onHero,
            shape = AppTheme.shapes.card,
            modifier = Modifier.fillMaxWidth().scale(scale.value),
        ) {
            Column(Modifier.padding(24.dp)) {
                Text(
                    text = state.tomorrow.size.toString(),
                    style = AppTheme.type.number.merge(MaterialTheme.typography.displayLarge),
                    color = AppTheme.colors.heroAccent,
                )
                Text(pluralStringResource(R.plurals.plan_done_tasks, state.tomorrow.size), style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.size(8.dp))
                Text(
                    if (state.priorities == 0) {
                        stringResource(R.string.plan_done_no_priorities)
                    } else {
                        pluralStringResource(R.plurals.plan_done_priorities, state.priorities, state.priorities)
                    },
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
        }
        Text(outcome(state), style = MaterialTheme.typography.bodyLarge, color = AppTheme.colors.textMuted)
        Button(onClick = onClose, modifier = Modifier.align(Alignment.End)) { Text(stringResource(R.string.plan_back_today)) }
    }
}

@Composable
private fun outcome(state: PlanUiState): String {
    val parts = listOfNotNull(
        state.decided(PlanDecision.TOMORROW).takeIf { it > 0 }?.let { pluralStringResource(R.plurals.plan_outcome_tomorrow, it, it) },
        state.decided(PlanDecision.LATER).takeIf { it > 0 }?.let { pluralStringResource(R.plurals.plan_outcome_later, it, it) },
        state.decided(PlanDecision.DONE).takeIf { it > 0 }?.let { pluralStringResource(R.plurals.plan_outcome_done, it, it) },
        state.decided(PlanDecision.DROPPED).takeIf { it > 0 }?.let { pluralStringResource(R.plurals.plan_outcome_dropped, it, it) },
    )
    return if (parts.isEmpty()) stringResource(R.string.plan_outcome_none) else stringResource(R.string.plan_outcome, parts.joinToString(" · "))
}

@Composable
private fun Intro(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyLarge,
        color = AppTheme.colors.textMuted,
        modifier = Modifier.padding(start = 4.dp, end = 4.dp, bottom = 8.dp),
    )
}

/** The calendar for "Date": days after today; picking tomorrow is the same as Tomorrow. */
@Composable
private fun LaterPicker(today: LocalDate, current: LocalDate?, onPick: (LocalDate) -> Unit, onDismiss: () -> Unit) {
    val state = rememberDatePickerState(
        initialSelectedDateMillis = current?.let(::toMillis),
        initialDisplayedMonthMillis = toMillis(current ?: today.plusDays(2)),
        selectableDates = object : SelectableDates {
            override fun isSelectableDate(utcTimeMillis: Long) = toDate(utcTimeMillis).isAfter(today)

            override fun isSelectableYear(year: Int) = year >= today.year
        },
    )
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = { state.selectedDateMillis?.let { onPick(toDate(it)) } }, enabled = state.selectedDateMillis != null) {
                Text(stringResource(R.string.plan_pick_date))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.plan_cancel)) } },
    ) {
        DatePicker(state = state)
    }
}

private fun toMillis(date: LocalDate): Long = date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()

private fun toDate(millis: Long): LocalDate = Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate()
