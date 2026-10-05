package com.goalmaker.app.ui.calendar

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowDropDown
import androidx.compose.material.icons.outlined.DateRange
import androidx.compose.material3.Button
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DateRangePicker
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDateRangePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import com.goalmaker.app.R
import com.goalmaker.app.application.planning.AreaItem
import com.goalmaker.app.application.planning.EventDraft
import com.goalmaker.app.application.planning.EventItem
import com.goalmaker.app.application.planning.EventRules
import com.goalmaker.app.ui.components.ScreenTitle
import com.goalmaker.app.ui.theme.AppTheme
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import kotlinx.coroutines.launch

/**
 * Adds a calendar event or edits one (docs/calendar.md, Events): the title, the first and last day
 * from a range picker, the area and the notes. [onSave] says whether the event was kept, so the sheet
 * stays open when it was not; [onDelete] is there for an event that already exists, and the place
 * that opened the sheet offers the undo. A new event starts from [firstDay] to [lastDay].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EventSheet(
    initial: EventItem?,
    firstDay: LocalDate,
    areas: List<AreaItem>,
    onSave: suspend (EventDraft) -> Boolean,
    onDelete: (() -> Unit)?,
    onDismiss: () -> Unit,
    lastDay: LocalDate = firstDay,
) {
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    var title by rememberSaveable { mutableStateOf(initial?.title.orEmpty()) }
    var startsOn by rememberSaveable { mutableStateOf((initial?.startsOn ?: firstDay).toString()) }
    var endsOn by rememberSaveable { mutableStateOf((initial?.endsOn ?: lastDay).toString()) }
    var notes by rememberSaveable { mutableStateOf(initial?.notes.orEmpty()) }
    var areaId by rememberSaveable { mutableStateOf(initial?.areaId) }
    var picking by remember { mutableStateOf(false) }
    val first = LocalDate.parse(startsOn)
    val last = LocalDate.parse(endsOn)
    val canSave = title.isNotBlank() && EventRules.validSpan(first, last)

    fun save() {
        if (!canSave) return
        val draft = EventDraft(title = title, startsOn = first, endsOn = last, notes = notes, areaId = areaId)
        scope.launch {
            if (onSave(draft)) {
                sheet.hide()
                onDismiss()
            }
        }
    }

    if (picking) {
        DaysPicker(first, last, onPick = { from, to ->
            startsOn = from.toString()
            endsOn = to.toString()
            picking = false
        }, onDismiss = { picking = false })
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheet) {
        Column(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = AppTheme.density.pagePadding.dp)
                .padding(bottom = 16.dp)
                .navigationBarsPadding(),
        ) {
            ScreenTitle(stringResource(if (initial == null) R.string.event_add else R.string.event_edit))
            OutlinedTextField(
                value = title,
                onValueChange = { title = it.take(EventRules.MAX_TITLE) },
                label = { Text(stringResource(R.string.event_title_field)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                modifier = Modifier.fillMaxWidth(),
            )
            Label(stringResource(R.string.event_days))
            OutlinedButton(onClick = { picking = true }, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Outlined.DateRange, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(eventDays(first, last), modifier = Modifier.weight(1f))
            }
            if (!EventRules.validSpan(first, last)) {
                Text(stringResource(R.string.event_too_long), style = MaterialTheme.typography.bodySmall, color = AppTheme.colors.danger)
            }
            Label(stringResource(R.string.event_area))
            AreaPicker(areas, areaId, onPick = { areaId = it })
            OutlinedTextField(
                value = notes,
                onValueChange = { notes = it.take(EventRules.MAX_NOTES) },
                label = { Text(stringResource(R.string.event_notes_field)) },
                minLines = 3,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                modifier = Modifier.fillMaxWidth(),
            )
            Row {
                if (onDelete != null) {
                    TextButton(onClick = {
                        onDelete()
                        onDismiss()
                    }) { Text(stringResource(R.string.event_delete), color = AppTheme.colors.danger) }
                }
                Spacer(Modifier.weight(1f))
                Button(onClick = ::save, enabled = canSave) { Text(stringResource(R.string.event_save)) }
            }
            Spacer(Modifier.height(4.dp))
        }
    }
}

@Composable
private fun Label(text: String) {
    Text(text.uppercase(), style = MaterialTheme.typography.labelSmall, color = AppTheme.colors.accent, fontWeight = FontWeight.Bold)
}

/** The event's area, or none; archived areas leave the menu, but the event's own stays listed. */
@Composable
private fun AreaPicker(areas: List<AreaItem>, areaId: String?, onPick: (String?) -> Unit) {
    var open by remember { mutableStateOf(false) }
    val area = areas.firstOrNull { it.id == areaId }
    Box {
        OutlinedButton(onClick = { open = true }, modifier = Modifier.fillMaxWidth()) {
            Text(area?.let { listOfNotNull(it.emoji, it.name).joinToString(" ") } ?: stringResource(R.string.event_no_area), modifier = Modifier.weight(1f))
            Icon(Icons.Outlined.ArrowDropDown, contentDescription = null)
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(text = { Text(stringResource(R.string.event_no_area)) }, onClick = {
                open = false
                onPick(null)
            })
            areas.filter { !it.archived || it.id == areaId }.forEach { choice ->
                DropdownMenuItem(text = { Text(listOfNotNull(choice.emoji, choice.name).joinToString(" ")) }, onClick = {
                    open = false
                    onPick(choice.id)
                })
            }
        }
    }
}

/** The range picker for the event's first and last day; picking only a first day makes a one-day event. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DaysPicker(first: LocalDate, last: LocalDate, onPick: (LocalDate, LocalDate) -> Unit, onDismiss: () -> Unit) {
    val state = rememberDateRangePickerState(
        initialSelectedStartDateMillis = toMillis(first),
        initialSelectedEndDateMillis = toMillis(last),
        initialDisplayedMonthMillis = toMillis(first),
    )
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = {
                    val from = state.selectedStartDateMillis?.let(::toDate) ?: return@TextButton
                    onPick(from, state.selectedEndDateMillis?.let(::toDate) ?: from)
                },
                enabled = state.selectedStartDateMillis != null,
            ) { Text(stringResource(R.string.event_ok)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.event_cancel)) } },
    ) {
        DateRangePicker(state = state, title = { Text(stringResource(R.string.event_pick_days), modifier = Modifier.padding(start = 24.dp, top = 16.dp)) }, modifier = Modifier.weight(1f))
    }
}

private fun toMillis(date: LocalDate): Long = date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()

private fun toDate(millis: Long): LocalDate = Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate()
