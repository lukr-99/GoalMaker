package com.goalmaker.app.ui.habits

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import com.goalmaker.app.R
import com.goalmaker.app.ui.settings.SwitchRow
import java.time.LocalTime
import java.time.format.DateTimeFormatter

/**
 * The habit form's reminder (docs/reminders.md): a switch, and while it is on the time it rings on the
 * days the habit is still left, which a tap changes. [time] null is no reminder.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun HabitRemindField(time: LocalTime?, onChange: (LocalTime?) -> Unit) {
    var picking by remember { mutableStateOf(false) }
    Column {
        SwitchRow(
            title = stringResource(R.string.habits_remind),
            hint = stringResource(R.string.habits_remind_hint),
            checked = time != null,
            onCheckedChange = { on -> onChange(if (on) DEFAULT else null) },
        )
        if (time != null) {
            TextButton(onClick = { picking = true }) {
                Text(stringResource(R.string.habits_remind_at, time.format(DateTimeFormatter.ofPattern("HH:mm"))))
            }
        }
    }
    if (picking) {
        val initial = time ?: DEFAULT
        val state = rememberTimePickerState(initialHour = initial.hour, initialMinute = initial.minute, is24Hour = true)
        AlertDialog(
            onDismissRequest = { picking = false },
            text = { TimePicker(state = state) },
            confirmButton = {
                TextButton(onClick = {
                    onChange(LocalTime.of(state.hour, state.minute))
                    picking = false
                }) { Text(stringResource(R.string.task_ok)) }
            },
            dismissButton = { TextButton(onClick = { picking = false }) { Text(stringResource(R.string.habits_cancel)) } },
        )
    }
}

// A habit reminder starts at eight in the evening, the Plan tomorrow reminder's time, until changed.
private val DEFAULT: LocalTime = LocalTime.of(20, 0)
