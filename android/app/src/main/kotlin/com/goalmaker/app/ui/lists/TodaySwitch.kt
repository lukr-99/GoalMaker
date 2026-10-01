package com.goalmaker.app.ui.lists

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Checklist
import androidx.compose.material.icons.outlined.DonutLarge
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.goalmaker.app.R
import com.goalmaker.app.ui.theme.AppTheme

/**
 * Today's switch between its tasks and its habits (the habits prototype, option C), each half with
 * what is still open: tasks to do, habits left, or a check once every habit is done.
 */
@Composable
fun TodaySwitch(
    shown: TodaySegment,
    openTasks: Int,
    habitsLeft: Int,
    habitsDone: Boolean,
    onShow: (TodaySegment) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        modifier = modifier
            .fillMaxWidth()
            .background(AppTheme.colors.surfaceVariant, CircleShape)
            .padding(4.dp)
            .selectableGroup(),
    ) {
        Half(
            selected = shown == TodaySegment.TASKS,
            icon = Icons.Outlined.Checklist,
            label = stringResource(R.string.today_segment_tasks),
            count = openTasks.toString(),
            description = pluralStringResource(R.plurals.today_segment_tasks_description, openTasks, openTasks),
            onClick = { onShow(TodaySegment.TASKS) },
            modifier = Modifier.weight(1f),
        )
        Half(
            selected = shown == TodaySegment.HABITS,
            icon = Icons.Outlined.DonutLarge,
            label = stringResource(R.string.today_segment_habits),
            count = if (habitsDone) null else habitsLeft.toString(),
            description = if (habitsDone) {
                stringResource(R.string.today_segment_habits_done)
            } else {
                pluralStringResource(R.plurals.today_segment_habits_description, habitsLeft, habitsLeft)
            },
            onClick = { onShow(TodaySegment.HABITS) },
            modifier = Modifier.weight(1f),
        )
    }
}

// One half of the switch: the accent fills it while it is shown. A null count shows a check.
@Composable
private fun Half(
    selected: Boolean,
    icon: ImageVector,
    label: String,
    count: String?,
    description: String,
    onClick: () -> Unit,
    modifier: Modifier,
) {
    val colors = AppTheme.colors
    val duration = if (AppTheme.reduceMotion) 0 else AppTheme.motion.standard
    val fill by animateColorAsState(if (selected) colors.accent else colors.surfaceVariant, tween(duration), label = "segment fill")
    val content by animateColorAsState(if (selected) colors.onAccent else colors.textMuted, tween(duration), label = "segment text")
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
        modifier = modifier
            .heightIn(min = 48.dp)
            .background(fill, CircleShape)
            .selectable(selected = selected, role = Role.Tab, onClick = onClick)
            .clearAndSetSemantics {
                contentDescription = description
                role = Role.Tab
                this.selected = selected
            },
    ) {
        Icon(icon, contentDescription = null, tint = content, modifier = Modifier.size(18.dp))
        Text(
            label,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = content,
            modifier = Modifier.padding(start = 8.dp),
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .padding(start = 8.dp)
                .background(content.copy(alpha = 0.18f), CircleShape)
                .padding(horizontal = 7.dp, vertical = 1.dp),
        ) {
            if (count == null) {
                Icon(Icons.Outlined.Check, contentDescription = null, tint = content, modifier = Modifier.size(14.dp))
            } else {
                Text(count, style = AppTheme.type.number.merge(MaterialTheme.typography.labelMedium), fontWeight = FontWeight.ExtraBold, color = content)
            }
        }
    }
}
