package com.goalmaker.app.ui.task

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MediumFlexibleTopAppBar
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.goalmaker.app.R
import com.goalmaker.app.application.planning.GoalHorizon
import com.goalmaker.app.application.planning.StepItem
import com.goalmaker.app.application.planning.TagItem
import com.goalmaker.app.application.planning.TaskItem
import com.goalmaker.app.application.planning.TaskState
import com.goalmaker.app.ui.composer.describeRepeat
import com.goalmaker.app.ui.nav.sharedTaskBounds
import com.goalmaker.app.ui.theme.AppTheme
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/**
 * One task's detail view (spec stories 12 to 19, docs/archive.md): title and done box, notes in light
 * Markdown, day and time, deadline, area, tags, repeat and the checklist. Leaving a text field saves
 * it; a deleted task closes the view.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TaskScreen(viewModel: TaskViewModel, onBack: () -> Unit) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    LaunchedEffect(state.loaded, state.task == null) {
        if (state.loaded && state.task == null) onBack()
    }
    val task = state.task ?: return

    Scaffold(
        modifier = Modifier
            .sharedTaskBounds(task.id, AppTheme.shapes.row, details = true)
            .nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            MediumFlexibleTopAppBar(
                title = { Text(AppTheme.headline(stringResource(R.string.task_title))) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.settings_back))
                    }
                },
                actions = {
                    IconButton(onClick = viewModel::delete) {
                        Icon(Icons.Outlined.Delete, contentDescription = stringResource(R.string.task_delete))
                    }
                },
                scrollBehavior = scrollBehavior,
            )
        },
    ) { padding ->
        Column(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = AppTheme.density.pagePadding.dp, vertical = 8.dp),
        ) {
            TitleField(task, onDone = viewModel::setDone, onRename = viewModel::rename)
            Notes(task.notes, onSave = viewModel::setNotes)
            HorizontalDivider()
            Schedule(task, onSchedule = viewModel::schedule, onDeadline = viewModel::setDeadline)
            AreaField(task, state, onArea = viewModel::setArea)
            GoalField(task, state, onGoal = viewModel::setGoal)
            RepeatField(task, onRepeat = viewModel::setRecurrence)
            HorizontalDivider()
            Tags(state, onToggle = viewModel::toggleTag, onAdd = viewModel::addTag)
            HorizontalDivider()
            Steps(
                steps = state.steps,
                onAdd = viewModel::addStep,
                onToggle = viewModel::toggleStep,
                onRename = viewModel::renameStep,
                onMove = viewModel::moveStep,
                onDelete = viewModel::deleteStep,
            )
        }
    }
}

@Composable
private fun TitleField(task: TaskItem, onDone: (Boolean) -> Unit, onRename: (String) -> Unit) {
    var title by rememberSaveable(task.id, task.title) { mutableStateOf(task.title) }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Checkbox(checked = task.state == TaskState.DONE, onCheckedChange = onDone)
        OutlinedTextField(
            value = title,
            onValueChange = { title = it },
            label = { Text(stringResource(R.string.task_title_label)) },
            textStyle = MaterialTheme.typography.titleMedium.copy(
                textDecoration = if (task.state == TaskState.DONE) TextDecoration.LineThrough else null,
            ),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { if (title.isBlank()) title = task.title else onRename(title) }),
            modifier = Modifier
                .weight(1f)
                .onFocusChanged { focus -> if (!focus.isFocused && title != task.title) { if (title.isBlank()) title = task.title else onRename(title) } },
        )
    }
}

@Composable
private fun Notes(notes: String, onSave: (String) -> Unit) {
    var editing by rememberSaveable { mutableStateOf(false) }
    var draft by rememberSaveable(notes) { mutableStateOf(notes) }
    Label(stringResource(R.string.task_notes))
    if (editing) {
        OutlinedTextField(
            value = draft,
            onValueChange = { draft = it.take(20_000) },
            supportingText = { Text(stringResource(R.string.task_notes_hint)) },
            minLines = 3,
            modifier = Modifier.fillMaxWidth(),
        )
        TextButton(onClick = {
            onSave(draft)
            editing = false
        }) { Text(stringResource(R.string.task_notes_save)) }
    } else {
        Box(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 40.dp)
                .clickable { editing = true }
                .padding(vertical = 8.dp),
        ) {
            if (notes.isBlank()) {
                Text(stringResource(R.string.task_notes_add), color = AppTheme.colors.textMuted)
            } else {
                MarkdownNotes(notes)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun Schedule(task: TaskItem, onSchedule: (LocalDate?, LocalTime?) -> Unit, onDeadline: (LocalDate?) -> Unit) {
    val locale = LocalConfiguration.current.locales[0]
    val dates = DateTimeFormatter.ofPattern("EEE d MMM yyyy", locale)
    val times = DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withLocale(locale)
    var picking by remember { mutableStateOf<String?>(null) }

    Field(
        label = stringResource(R.string.task_day),
        value = task.plannedDate?.format(dates) ?: stringResource(R.string.task_no_day),
        onClick = { picking = "day" },
        onClear = task.plannedDate?.let { { onSchedule(null, null) } },
    )
    Field(
        label = stringResource(R.string.task_time),
        value = task.plannedTime?.format(times) ?: stringResource(R.string.task_no_time),
        onClick = if (task.plannedDate != null) ({ picking = "time" }) else null,
        onClear = task.plannedTime?.let { { onSchedule(task.plannedDate, null) } },
    )
    Field(
        label = stringResource(R.string.task_deadline),
        value = task.deadline?.format(dates) ?: stringResource(R.string.task_no_deadline),
        onClick = { picking = "deadline" },
        onClear = task.deadline?.let { { onDeadline(null) } },
    )

    when (picking) {
        "day", "deadline" -> {
            val initial = if (picking == "day") task.plannedDate else task.deadline
            val pickerState = rememberDatePickerState(initialSelectedDateMillis = (initial ?: LocalDate.now()).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli())
            DatePickerDialog(
                onDismissRequest = { picking = null },
                confirmButton = {
                    TextButton(onClick = {
                        pickerState.selectedDateMillis?.let { millis ->
                            val day = Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate()
                            if (picking == "day") onSchedule(day, task.plannedTime) else onDeadline(day)
                        }
                        picking = null
                    }) { Text(stringResource(R.string.task_ok)) }
                },
                dismissButton = { TextButton(onClick = { picking = null }) { Text(stringResource(R.string.areas_cancel)) } },
            ) { DatePicker(state = pickerState) }
        }
        "time" -> {
            val initial = task.plannedTime ?: LocalTime.of(9, 0)
            val pickerState = rememberTimePickerState(initialHour = initial.hour, initialMinute = initial.minute, is24Hour = true)
            AlertDialog(
                onDismissRequest = { picking = null },
                text = { TimePicker(state = pickerState) },
                confirmButton = {
                    TextButton(onClick = {
                        onSchedule(task.plannedDate, LocalTime.of(pickerState.hour, pickerState.minute))
                        picking = null
                    }) { Text(stringResource(R.string.task_ok)) }
                },
                dismissButton = { TextButton(onClick = { picking = null }) { Text(stringResource(R.string.areas_cancel)) } },
            )
        }
    }
}

@Composable
private fun AreaField(task: TaskItem, state: TaskUiState, onArea: (String?) -> Unit) {
    var open by remember { mutableStateOf(false) }
    val area = state.areas.firstOrNull { it.id == task.areaId }
    Box {
        Field(
            label = stringResource(R.string.task_area),
            value = area?.let { listOfNotNull(it.emoji, it.name).joinToString(" ") } ?: stringResource(R.string.task_no_area),
            onClick = { open = true },
            onClear = area?.let { { onArea(null) } },
        )
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(text = { Text(stringResource(R.string.task_no_area)) }, onClick = {
                open = false
                onArea(null)
            })
            // Archived areas leave the picker, but the task's own area stays listed.
            state.areas.filter { !it.archived || it.id == task.areaId }.forEach { choice ->
                DropdownMenuItem(text = { Text(listOfNotNull(choice.emoji, choice.name).joinToString(" ")) }, onClick = {
                    open = false
                    onArea(choice.id)
                })
            }
        }
    }
}

@Composable
private fun GoalField(task: TaskItem, state: TaskUiState, onGoal: (String?) -> Unit) {
    var open by remember { mutableStateOf(false) }
    val goal = state.goals.firstOrNull { it.id == task.goalId }
    Box {
        Field(
            label = stringResource(R.string.task_goal),
            value = goal?.let { listOfNotNull(it.emoji, it.title).joinToString(" ") } ?: stringResource(R.string.task_no_goal),
            onClick = { open = true },
            onClear = goal?.let { { onGoal(null) } },
        )
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(text = { Text(stringResource(R.string.task_no_goal)) }, onClick = {
                open = false
                onGoal(null)
            })
            state.goals.forEach { choice ->
                DropdownMenuItem(
                    text = { Text(listOfNotNull(choice.emoji, choice.title).joinToString(" ")) },
                    trailingIcon = {
                        Text(
                            stringResource(
                                when (choice.horizon) {
                                    GoalHorizon.YEAR -> R.string.goals_horizon_year
                                    GoalHorizon.MONTH -> R.string.goals_horizon_month
                                    GoalHorizon.WEEK -> R.string.goals_horizon_week
                                    GoalHorizon.DAY -> R.string.goals_horizon_day
                                },
                            ),
                            style = MaterialTheme.typography.labelSmall,
                            color = AppTheme.colors.textMuted,
                        )
                    },
                    onClick = {
                        open = false
                        onGoal(choice.id)
                    },
                )
            }
        }
    }
}

@Composable
private fun RepeatField(task: TaskItem, onRepeat: (String?) -> Unit) {
    var open by remember { mutableStateOf(false) }
    val locale = LocalConfiguration.current.locales[0]
    val anchor = task.plannedDate ?: LocalDate.now()
    val weekday = anchor.dayOfWeek
    val presets = listOf(
        "FREQ=DAILY",
        "FREQ=WEEKLY;BYDAY=MO,TU,WE,TH,FR",
        "FREQ=WEEKLY;BYDAY=" + weekday.name.take(2),
        "FREQ=MONTHLY;BYMONTHDAY=" + anchor.dayOfMonth,
    )
    Box {
        Field(
            label = stringResource(R.string.task_repeat),
            value = task.recurrence?.let { describeRepeat(it, locale) } ?: stringResource(R.string.task_no_repeat),
            onClick = { open = true },
            onClear = task.recurrence?.let { { onRepeat(null) } },
        )
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(text = { Text(stringResource(R.string.task_no_repeat)) }, onClick = {
                open = false
                onRepeat(null)
            })
            presets.forEach { rule ->
                DropdownMenuItem(text = { Text(describeRepeat(rule, locale)) }, onClick = {
                    open = false
                    onRepeat(rule)
                })
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Tags(state: TaskUiState, onToggle: (TagItem) -> Unit, onAdd: (String) -> Unit) {
    var name by rememberSaveable { mutableStateOf("") }
    Label(stringResource(R.string.task_tags))
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        state.tags.forEach { tag ->
            FilterChip(selected = tag.id in state.taskTagIds, onClick = { onToggle(tag) }, label = { Text("#${tag.name}") })
        }
    }
    OutlinedTextField(
        value = name,
        onValueChange = { name = it },
        label = { Text(stringResource(R.string.task_new_tag)) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = {
            onAdd(name)
            name = ""
        }),
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun Steps(
    steps: List<StepItem>,
    onAdd: (String) -> Unit,
    onToggle: (StepItem) -> Unit,
    onRename: (String, String) -> Unit,
    onMove: (String, Int) -> Unit,
    onDelete: (String) -> Unit,
) {
    var name by rememberSaveable { mutableStateOf("") }
    var renaming by remember { mutableStateOf<StepItem?>(null) }
    Label(stringResource(R.string.task_steps))
    steps.forEachIndexed { index, step ->
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Checkbox(checked = step.done, onCheckedChange = { onToggle(step) })
            Text(
                step.title,
                style = MaterialTheme.typography.bodyLarge.copy(textDecoration = if (step.done) TextDecoration.LineThrough else null),
                modifier = Modifier.weight(1f).clickable { renaming = step }.padding(vertical = 8.dp),
            )
            IconButton(onClick = { onMove(step.id, -1) }, enabled = index > 0) {
                Icon(Icons.Outlined.KeyboardArrowUp, contentDescription = stringResource(R.string.task_step_up, step.title))
            }
            IconButton(onClick = { onMove(step.id, 1) }, enabled = index < steps.lastIndex) {
                Icon(Icons.Outlined.KeyboardArrowDown, contentDescription = stringResource(R.string.task_step_down, step.title))
            }
            IconButton(onClick = { onDelete(step.id) }) {
                Icon(Icons.Outlined.Close, contentDescription = stringResource(R.string.task_step_delete, step.title))
            }
        }
    }
    OutlinedTextField(
        value = name,
        onValueChange = { name = it },
        label = { Text(stringResource(R.string.task_add_step)) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = {
            onAdd(name)
            name = ""
        }),
        modifier = Modifier.fillMaxWidth(),
    )

    renaming?.let { step ->
        var title by remember(step.id) { mutableStateOf(step.title) }
        AlertDialog(
            onDismissRequest = { renaming = null },
            title = { Text(stringResource(R.string.task_step_rename)) },
            text = { OutlinedTextField(value = title, onValueChange = { title = it }, singleLine = true) },
            confirmButton = {
                TextButton(onClick = {
                    onRename(step.id, title)
                    renaming = null
                }) { Text(stringResource(R.string.areas_save)) }
            },
            dismissButton = { TextButton(onClick = { renaming = null }) { Text(stringResource(R.string.areas_cancel)) } },
        )
    }
}

@Composable
private fun Label(text: String) {
    Text(text, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
}

@Composable
private fun Field(label: String, value: String, onClick: (() -> Unit)?, onClear: (() -> Unit)?) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
    ) {
        Text(label, style = MaterialTheme.typography.titleSmall, color = AppTheme.colors.accent, modifier = Modifier.weight(0.35f))
        Text(
            value,
            style = MaterialTheme.typography.bodyLarge,
            color = if (onClick == null) AppTheme.colors.textMuted else MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(0.65f),
        )
        if (onClear != null) {
            IconButton(onClick = onClear) {
                Icon(Icons.Outlined.Close, contentDescription = stringResource(R.string.task_clear, label))
            }
        }
    }
}
