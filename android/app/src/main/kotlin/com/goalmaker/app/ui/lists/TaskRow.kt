package com.goalmaker.app.ui.lists

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Repeat
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.goalmaker.app.R
import com.goalmaker.app.application.planning.AreaItem
import com.goalmaker.app.application.planning.ProjectItem
import com.goalmaker.app.application.planning.TaskItem
import com.goalmaker.app.ui.components.GoalMakerCheckbox
import com.goalmaker.app.ui.components.ProjectChip
import com.goalmaker.app.ui.nav.sharedTaskBounds
import com.goalmaker.app.ui.theme.AppTheme
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * A task in a list: the theme's checkbox, the title, and what else it says (time, area, its
 * [project]'s chip, repeat, top priority, a waiting reminder). Checking it plays the check, a haptic
 * and the optional tick, then the row leaves. Swiping it away deletes it; both offer undo. A tap
 * opens the task's details, a long press its reminders (docs/reminders.md), the chip the project's
 * board ([onOpenProject]). TalkBack reads the row as one item with delete, remind and open the board
 * as its actions, and the checkbox, named by the title, as its own control (M6-05).
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TaskRow(
    task: TaskItem,
    area: AreaItem?,
    showDay: Boolean,
    onComplete: () -> Unit,
    onDelete: () -> Unit,
    onRemind: () -> Unit,
    onOpen: () -> Unit,
    reminded: Boolean,
    tick: () -> Unit,
    modifier: Modifier = Modifier,
    project: ProjectItem? = null,
    onOpenProject: (() -> Unit)? = null,
) {
    var checked by remember(task.id) { mutableStateOf(false) }
    val haptics = LocalHapticFeedback.current
    val reduceMotion = AppTheme.reduceMotion
    val sound = AppTheme.completionSound
    val settle = if (reduceMotion) 0L else AppTheme.motion.emphasized.toLong()
    LaunchedEffect(checked) {
        if (checked) {
            delay(settle)
            onComplete()
        }
    }
    val dismiss = rememberSwipeToDismissBoxState()
    val scope = rememberCoroutineScope()
    val deleteLabel = stringResource(R.string.lists_delete, task.title)
    val remindLabel = stringResource(R.string.reminder_add)
    val openProjectLabel = project?.let { stringResource(R.string.lists_open_project, it.name) }
    val doneLabel = stringResource(R.string.lists_done_box, task.title)
    SwipeToDismissBox(
        state = dismiss,
        enableDismissFromStartToEnd = false,
        onDismiss = {
            onDelete()
            // The list saves this state per task; left dismissed, an undone row would delete itself again.
            scope.launch { dismiss.reset() }
        },
        // Only while a swipe is under way: the row also lifts off when it grows into the task's details.
        backgroundContent = { if (dismiss.dismissDirection == SwipeToDismissBoxValue.EndToStart) DeleteBackground() },
        modifier = modifier,
    ) {
        // The tap target is the item a screen reader stops on, so the actions live there too.
        Surface(
            color = AppTheme.colors.surface,
            shape = AppTheme.shapes.row,
            modifier = Modifier
                .sharedTaskBounds(task.id, AppTheme.shapes.row, details = false)
                .fillMaxWidth()
                .combinedClickable(onLongClick = onRemind, onClick = onOpen)
                .semantics {
                    customActions = listOfNotNull(
                        CustomAccessibilityAction(deleteLabel) { onDelete(); true },
                        CustomAccessibilityAction(remindLabel) { onRemind(); true },
                        if (openProjectLabel != null && onOpenProject != null) CustomAccessibilityAction(openProjectLabel) { onOpenProject(); true } else null,
                    )
                },
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .heightIn(min = AppTheme.density.rowMinHeight.dp)
                    .padding(start = 4.dp, end = 16.dp),
            ) {
                GoalMakerCheckbox(
                    checked = checked,
                    onCheckedChange = { value ->
                        if (value && !checked) {
                            checked = true
                            haptics.performHapticFeedback(HapticFeedbackType.ToggleOn)
                            if (sound) tick()
                        }
                    },
                    modifier = Modifier.semantics { contentDescription = doneLabel },
                )
                // The row's tap merges the title, what is under it and the time into one thing to read (M6-05).
                Column(
                    Modifier
                        .weight(1f)
                        .padding(vertical = 10.dp),
                ) {
                    Text(task.title, style = MaterialTheme.typography.bodyLarge, maxLines = 3, overflow = TextOverflow.Ellipsis)
                    TaskDetails(task, area, showDay, project = project, onOpenProject = onOpenProject)
                }
                if (reminded) {
                    Icon(
                        Icons.Outlined.Notifications,
                        contentDescription = stringResource(R.string.lists_reminded),
                        tint = AppTheme.colors.textMuted,
                        modifier = Modifier.size(16.dp),
                    )
                }
                task.plannedTime?.let { TaskTime(it) }
            }
        }
    }
}

/** A task's time, in the theme's number style and accent. */
@Composable
internal fun TaskTime(time: LocalTime, modifier: Modifier = Modifier) {
    Text(
        text = DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withLocale(LocalConfiguration.current.locales[0]).format(time),
        style = AppTheme.type.number.merge(MaterialTheme.typography.titleMedium),
        color = AppTheme.colors.accent,
        modifier = modifier.padding(start = 8.dp),
    )
}

/**
 * The line under a task's title: its day when [showDay] (overdue), top priority unless the row
 * shows it elsewhere ([showPriority] false), area, the [project]'s chip and repeat. It wraps when a
 * narrow screen can't hold it all on one line.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun TaskDetails(
    task: TaskItem,
    area: AreaItem?,
    showDay: Boolean,
    showPriority: Boolean = true,
    project: ProjectItem? = null,
    onOpenProject: (() -> Unit)? = null,
) {
    val parts = task.recurrence != null || area != null || project != null ||
        (showPriority && task.topPriority) || (showDay && task.plannedDate != null)
    if (!parts) return
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
        itemVerticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(top = 2.dp),
    ) {
        if (showDay) {
            task.plannedDate?.let { date ->
                Text(
                    DateTimeFormatter.ofPattern("EEE d MMM", LocalConfiguration.current.locales[0]).format(date),
                    style = MaterialTheme.typography.bodySmall,
                    color = AppTheme.colors.danger,
                )
            }
        }
        if (showPriority && task.topPriority) {
            Icon(Icons.Outlined.Flag, contentDescription = stringResource(R.string.composer_priority), tint = AppTheme.colors.accent, modifier = Modifier.size(14.dp))
        }
        area?.let {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                val palette = AppTheme.areaColors.firstOrNull { color -> color.id == it.colorId }
                Box(Modifier.size(8.dp).background(palette?.let { color -> AppTheme.colors.areaContent(color) } ?: AppTheme.colors.outline, CircleShape))
                Text(it.name, style = MaterialTheme.typography.bodySmall, color = AppTheme.colors.textMuted)
            }
        }
        project?.let { ProjectChip(it, task.itemType, onOpenProject) }
        if (task.recurrence != null) {
            Icon(Icons.Outlined.Repeat, contentDescription = stringResource(R.string.lists_repeats), tint = AppTheme.colors.textMuted, modifier = Modifier.size(14.dp))
        }
    }
}

@Composable
private fun DeleteBackground() {
    Box(
        contentAlignment = Alignment.CenterEnd,
        modifier = Modifier
            .fillMaxSize()
            .background(AppTheme.colors.danger, AppTheme.shapes.row)
            .padding(end = 20.dp),
    ) {
        Icon(Icons.Outlined.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.onError)
    }
}
