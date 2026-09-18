package com.goalmaker.app.ui.lists

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.goalmaker.app.R
import com.goalmaker.app.application.planning.ReminderItem
import com.goalmaker.app.application.planning.TaskItem
import com.goalmaker.app.ui.theme.AppTheme
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/**
 * The reminders on one task (docs/reminders.md): the choices that make sense for it, and what is
 * already set. Relative choices only appear when the task has a time to count back from.
 */
@Composable
fun ReminderSheet(
    task: TaskItem,
    reminders: List<ReminderItem>,
    onRemindWhenDue: () -> Unit,
    onRemindBefore: (Int) -> Unit,
    onRemindInAnHour: () -> Unit,
    onRemindTomorrowMorning: () -> Unit,
    onRemove: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth().padding(start = 24.dp, end = 24.dp, bottom = 32.dp)) {
            Text(task.title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(bottom = 4.dp))
            Text(
                stringResource(R.string.reminder_add),
                style = MaterialTheme.typography.bodySmall,
                color = AppTheme.colors.textMuted,
                modifier = Modifier.padding(bottom = 12.dp),
            )
            val timed = task.plannedDate != null && task.plannedTime != null
            if (timed) {
                Choice(stringResource(R.string.reminder_when_due), onRemindWhenDue)
                Choice(stringResource(R.string.reminder_before_quarter)) { onRemindBefore(QUARTER_HOUR) }
                Choice(stringResource(R.string.reminder_before_hour)) { onRemindBefore(HOUR) }
            }
            Choice(stringResource(R.string.reminder_in_hour), onRemindInAnHour)
            Choice(stringResource(R.string.reminder_tomorrow_morning), onRemindTomorrowMorning)

            if (reminders.isNotEmpty()) {
                Text(
                    stringResource(R.string.reminder_existing),
                    style = MaterialTheme.typography.bodySmall,
                    color = AppTheme.colors.textMuted,
                    modifier = Modifier.padding(top = 16.dp, bottom = 4.dp),
                )
                reminders.forEach { reminder -> Existing(reminder) { onRemove(reminder.id) } }
            }
        }
    }
}

@Composable
private fun Choice(label: String, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 14.dp),
    ) {
        Icon(Icons.Outlined.Notifications, contentDescription = null, tint = AppTheme.colors.textMuted, modifier = Modifier.size(20.dp))
        Text(label, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun Existing(reminder: ReminderItem, onRemove: () -> Unit) {
    val locale = LocalConfiguration.current.locales[0]
    val label = reminder.fireAt?.let { DateTimeFormatter.ofLocalizedDateTime(FormatStyle.SHORT).withLocale(locale).format(it) }
        ?: reminder.offsetMinutes?.let { minutes ->
            when (-minutes) {
                0 -> stringResource(R.string.reminder_when_due)
                QUARTER_HOUR -> stringResource(R.string.reminder_before_quarter)
                else -> stringResource(R.string.reminder_before_hour)
            }
        }
        .orEmpty()
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        IconButton(onClick = onRemove) {
            Icon(Icons.Outlined.Close, contentDescription = stringResource(R.string.reminder_remove), modifier = Modifier.size(18.dp))
        }
    }
}

private const val QUARTER_HOUR = 15
private const val HOUR = 60
