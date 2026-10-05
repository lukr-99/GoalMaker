package com.goalmaker.app.ui.calendar

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.ToggleButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.goalmaker.app.R
import com.goalmaker.app.ui.theme.AppTheme

/**
 * What the calendar's bottom bar adds, over its line (docs/calendar.md): a Task or Event switch on one
 * day, and across several picked days the choice between a task on each day and one event. While
 * picking it also counts the days ("3 days") and offers Done, which goes back to one day. When the
 * pick is [tooWide] to be one event, it says so, and the bar does not send.
 */
@Composable
fun AddKindRow(
    picking: Boolean,
    days: Int,
    event: Boolean,
    tooWide: Boolean,
    onChoose: (event: Boolean) -> Unit,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val several = days > 1
    Column(modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        if (picking) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    pluralStringResource(R.plurals.calendar_picked_days, days, days),
                    style = MaterialTheme.typography.titleSmall,
                    color = AppTheme.colors.text,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = onDone) { Text(stringResource(R.string.calendar_pick_done)) }
            }
        }
        if (tooWide) {
            Text(
                stringResource(R.string.event_too_long),
                style = MaterialTheme.typography.bodySmall,
                color = AppTheme.colors.danger,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            )
        }
        val label = stringResource(if (several) R.string.calendar_add_across else R.string.calendar_add_kind)
        Row(
            horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween),
            modifier = Modifier
                .fillMaxWidth()
                .selectableGroup()
                .semantics { contentDescription = label },
        ) {
            listOf(
                false to if (several) R.string.calendar_add_task_each else R.string.calendar_add_task,
                true to if (several) R.string.calendar_add_one_event else R.string.calendar_add_event,
            ).forEachIndexed { index, (value, text) ->
                ToggleButton(
                    checked = event == value,
                    onCheckedChange = { if (event != value) onChoose(value) },
                    shapes = if (index == 0) ButtonGroupDefaults.connectedLeadingButtonShapes() else ButtonGroupDefaults.connectedTrailingButtonShapes(),
                    modifier = Modifier.weight(1f).semantics { role = Role.RadioButton },
                ) {
                    Text(text = stringResource(text), maxLines = 2, textAlign = TextAlign.Center)
                }
            }
        }
    }
}
