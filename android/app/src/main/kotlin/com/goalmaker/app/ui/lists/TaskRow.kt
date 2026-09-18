package com.goalmaker.app.ui.lists

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.goalmaker.app.R
import com.goalmaker.app.application.planning.AreaItem
import com.goalmaker.app.application.planning.TaskItem
import com.goalmaker.app.ui.components.GoalMakerCheckbox
import com.goalmaker.app.ui.theme.AppTheme
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * A task in a list: the theme's checkbox, the title, and what else it says (time, area, repeat,
 * top priority, a waiting reminder). Checking it plays the check, a haptic and the optional tick,
 * then the row leaves. Swiping it away deletes it; both offer undo. A long press opens its
 * reminders (docs/reminders.md). TalkBack gets both as actions.
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
    reminded: Boolean,
    tick: () -> Unit,
    modifier: Modifier = Modifier,
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
    SwipeToDismissBox(
        state = dismiss,
        enableDismissFromStartToEnd = false,
        onDismiss = {
            onDelete()
            // The list saves this state per task; left dismissed, an undone row would delete itself again.
            scope.launch { dismiss.reset() }
        },
        backgroundContent = { DeleteBackground() },
        modifier = modifier.semantics {
            customActions = listOf(
                CustomAccessibilityAction(deleteLabel) { onDelete(); true },
                CustomAccessibilityAction(remindLabel) { onRemind(); true },
            )
        },
    ) {
        Surface(
            color = AppTheme.colors.surface,
            shape = AppTheme.shapes.row,
            modifier = Modifier.fillMaxWidth().combinedClickable(onLongClick = onRemind, onClick = {}),
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
                )
                Column(Modifier.weight(1f).padding(vertical = 10.dp)) {
                    Text(task.title, style = MaterialTheme.typography.bodyLarge, maxLines = 3, overflow = TextOverflow.Ellipsis)
                    TaskDetails(task, area, showDay)
                }
                if (reminded) {
                    Icon(
                        Icons.Outlined.Notifications,
                        contentDescription = remindLabel,
                        tint = AppTheme.colors.textMuted,
                        modifier = Modifier.size(16.dp),
                    )
                }
                task.plannedTime?.let { TaskTime(it) }
            }
        }
    }
}

/** A task's time, in the theme's number style. */
@Composable
internal fun TaskTime(time: LocalTime, modifier: Modifier = Modifier) {
    Text(
        text = DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withLocale(LocalConfiguration.current.locales[0]).format(time),
        style = AppTheme.type.number.merge(MaterialTheme.typography.titleMedium),
        color = AppTheme.colors.textMuted,
        modifier = modifier.padding(start = 8.dp),
    )
}

/**
 * The line under a task's title: its day when [showDay] (overdue), top priority unless the row
 * shows it elsewhere ([showPriority] false), area and repeat.
 */
@Composable
internal fun TaskDetails(task: TaskItem, area: AreaItem?, showDay: Boolean, showPriority: Boolean = true) {
    val parts = task.recurrence != null || area != null || (showPriority && task.topPriority) || (showDay && task.plannedDate != null)
    if (!parts) return
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 2.dp)) {
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
