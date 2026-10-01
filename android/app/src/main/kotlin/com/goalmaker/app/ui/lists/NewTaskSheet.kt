package com.goalmaker.app.ui.lists

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import com.goalmaker.app.R
import com.goalmaker.app.application.planning.AreaItem
import com.goalmaker.app.ui.components.ChoiceChip
import com.goalmaker.app.ui.components.ScreenTitle
import com.goalmaker.app.ui.settings.SwitchRow
import com.goalmaker.app.ui.theme.AppTheme
import kotlinx.coroutines.launch

/**
 * The new task form, which the plus on Today, Tomorrow and the Inbox opens (docs/composer.md): a title,
 * when (starting on [day], the list's own), an area from [areas], top priority and notes. [onSave]
 * returns false when there is nothing to save, so the form stays open.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
internal fun NewTaskSheet(
    day: NewTaskDay,
    areas: List<AreaItem>,
    onSave: (title: String, day: NewTaskDay, area: AreaItem?, topPriority: Boolean, notes: String) -> Boolean,
    onDismiss: () -> Unit,
) {
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    var title by rememberSaveable { mutableStateOf("") }
    var picked by rememberSaveable { mutableStateOf(day) }
    var areaId by rememberSaveable { mutableStateOf<String?>(null) }
    var topPriority by rememberSaveable { mutableStateOf(false) }
    var notes by rememberSaveable { mutableStateOf("") }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }

    fun save() {
        if (onSave(title, picked, areas.firstOrNull { it.id == areaId }, topPriority, notes)) {
            scope.launch { sheet.hide() }.invokeOnCompletion { onDismiss() }
        }
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
            ScreenTitle(stringResource(R.string.bar_new_task))
            OutlinedTextField(
                value = title,
                onValueChange = { title = it.take(MAX_TITLE) },
                label = { Text(stringResource(R.string.task_form_title)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focus),
            )
            Label(stringResource(R.string.task_form_when))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(
                    NewTaskDay.TODAY to R.string.task_form_today,
                    NewTaskDay.TOMORROW to R.string.task_form_tomorrow,
                    NewTaskDay.NO_DAY to R.string.task_form_no_day,
                ).forEach { (choice, label) ->
                    ChoiceChip(selected = picked == choice, onClick = { picked = choice }, label = stringResource(label))
                }
            }
            val open = areas.filterNot(AreaItem::archived)
            if (open.isNotEmpty()) {
                Label(stringResource(R.string.task_form_area))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    open.forEach { area ->
                        ChoiceChip(
                            selected = areaId == area.id,
                            onClick = { areaId = if (areaId == area.id) null else area.id },
                            label = listOfNotNull(area.emoji, area.name).joinToString(" "),
                        )
                    }
                }
            }
            SwitchRow(
                title = stringResource(R.string.task_form_priority),
                hint = stringResource(R.string.task_form_priority_hint),
                checked = topPriority,
                onCheckedChange = { topPriority = it },
            )
            OutlinedTextField(
                value = notes,
                onValueChange = { notes = it.take(MAX_NOTES) },
                label = { Text(stringResource(R.string.task_form_notes)) },
                minLines = 3,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                modifier = Modifier.fillMaxWidth(),
            )
            Row {
                Spacer(Modifier.weight(1f))
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.task_form_cancel)) }
                Button(onClick = ::save, enabled = title.isNotBlank(), modifier = Modifier.padding(start = 8.dp)) {
                    Text(stringResource(R.string.task_form_save))
                }
            }
        }
    }
}

@Composable
private fun Label(text: String) {
    Text(text, style = MaterialTheme.typography.titleSmall, color = AppTheme.colors.accent)
}

// The task table's limits, as the composer keeps them.
private const val MAX_TITLE = 500
private const val MAX_NOTES = 10_000
