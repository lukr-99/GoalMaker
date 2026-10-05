package com.goalmaker.app.ui.wants

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Remove
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.goalmaker.app.R
import com.goalmaker.app.application.planning.WantDraft
import com.goalmaker.app.application.planning.WantItem
import com.goalmaker.app.application.planning.WantRules
import com.goalmaker.app.ui.components.ScreenTitle
import com.goalmaker.app.ui.theme.AppTheme
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.launch

/**
 * Adds a want or edits one (docs/wants.md). The reason is required; a new want shows the cooldown
 * its price gives, and the owner can pick another number of days before saving. Editing never
 * changes the cooldown already set. A new want can start from [prefill], what the bottom bar read
 * from a line it could not add as it stood; [onSaved] runs once the want is saved.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun WantSheet(
    viewModel: WantsViewModel,
    initial: WantItem?,
    initialTitle: String,
    onDismiss: () -> Unit,
    prefill: WantDraft? = null,
    onSaved: () -> Unit = {},
) {
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    var title by rememberSaveable { mutableStateOf(initialTitle) }
    var reason by rememberSaveable { mutableStateOf(initial?.reason ?: prefill?.reason.orEmpty()) }
    var priceText by rememberSaveable {
        mutableStateOf((initial?.price ?: prefill?.price)?.let { if (it % 1.0 == 0.0) it.toLong().toString() else it.toString() }.orEmpty())
    }
    var currency by rememberSaveable { mutableStateOf(initial?.currency ?: prefill?.currency ?: viewModel.uiState.value.cooldowns.currency) }
    var link by rememberSaveable { mutableStateOf(initial?.link.orEmpty()) }
    var picked by rememberSaveable { mutableStateOf(prefill?.pickedDays) }
    // A need skips the cooldown and need not say why (docs/wants.md, "Needs").
    val need = (initial?.kind ?: prefill?.kind) == WantRules.NEED
    var needBy by rememberSaveable { mutableStateOf((initial?.needBy ?: prefill?.needBy)?.toString()) }
    var pickingDay by remember { mutableStateOf(false) }
    val price = WantMoney.parse(priceText)
    val days = viewModel.cooldownFor(price, currency.trim().uppercase(), picked)
    val canSave = title.isNotBlank() && (need || reason.isNotBlank())

    fun save() {
        if (!canSave) return
        val draft = WantDraft(
            title = title,
            reason = reason,
            link = link,
            price = price,
            currency = currency,
            pickedDays = picked,
            kind = if (need) WantRules.NEED else WantRules.WANT,
            needBy = needBy?.let(LocalDate::parse),
        )
        scope.launch {
            val saved = if (initial == null) viewModel.add(draft) != null else viewModel.update(initial.id, draft)
            if (saved) {
                sheet.hide()
                onSaved()
            }
            onDismiss()
        }
    }

    if (pickingDay) {
        NeedByPicker(needBy?.let(LocalDate::parse), onPick = { needBy = it.toString(); pickingDay = false }, onDismiss = { pickingDay = false })
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
            ScreenTitle(
                stringResource(
                    when {
                        need && initial == null -> R.string.needs_add
                        need -> R.string.needs_edit
                        initial == null -> R.string.wants_add
                        else -> R.string.wants_edit
                    },
                ),
            )
            OutlinedTextField(
                value = title,
                onValueChange = { title = it.take(200) },
                label = { Text(stringResource(R.string.wants_title_field)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = reason,
                onValueChange = { reason = it.take(2000) },
                label = { Text(stringResource(if (need) R.string.needs_note_field else R.string.wants_reason_field)) },
                supportingText = if (need) null else ({ Text(stringResource(R.string.wants_reason_hint)) }),
                minLines = 2,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                modifier = Modifier.fillMaxWidth(),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = priceText,
                    onValueChange = { priceText = it.take(14) },
                    label = { Text(stringResource(R.string.wants_price)) },
                    singleLine = true,
                    isError = priceText.isNotBlank() && price == null,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.weight(1f),
                )
                OutlinedTextField(
                    value = currency,
                    onValueChange = { currency = it.take(3).uppercase() },
                    label = { Text(stringResource(R.string.wants_currency)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
                    modifier = Modifier.width(96.dp),
                )
            }
            OutlinedTextField(
                value = link,
                onValueChange = { link = it.take(2000) },
                label = { Text(stringResource(R.string.wants_link)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                modifier = Modifier.fillMaxWidth(),
            )
            if (need) {
                // The day it is needed by, optional.
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            stringResource(R.string.needs_by_field).uppercase(),
                            style = MaterialTheme.typography.labelSmall,
                            color = AppTheme.colors.accent,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            needBy?.let { LocalDate.parse(it).format(DateTimeFormatter.ofPattern("EEE d MMM", LocalConfiguration.current.locales[0])) }
                                ?: stringResource(R.string.needs_by_none),
                            style = MaterialTheme.typography.bodyLarge,
                            color = AppTheme.colors.text,
                        )
                    }
                    if (needBy != null) TextButton(onClick = { needBy = null }) { Text(stringResource(R.string.needs_by_clear)) }
                    TextButton(onClick = { pickingDay = true }) { Text(stringResource(R.string.needs_by_pick)) }
                }
            }
            if (initial == null && !need) {
                // The cooldown it will get, in the accent, and the owner can pick another.
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            stringResource(R.string.wants_cools_for).uppercase(),
                            style = MaterialTheme.typography.labelSmall,
                            color = AppTheme.colors.accent,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            pluralStringResource(R.plurals.wants_days, days, days),
                            style = AppTheme.type.number.copy(fontSize = MaterialTheme.typography.headlineSmall.fontSize),
                            color = AppTheme.colors.text,
                        )
                    }
                    IconButton(onClick = { picked = (days - 1).coerceAtLeast(0) }, enabled = days > 0) {
                        Icon(Icons.Outlined.Remove, contentDescription = stringResource(R.string.wants_fewer_days))
                    }
                    IconButton(onClick = { picked = (days + 1).coerceAtMost(WantRules.MAX_DAYS) }, enabled = days < WantRules.MAX_DAYS) {
                        Icon(Icons.Outlined.Add, contentDescription = stringResource(R.string.wants_more_days))
                    }
                }
            }
            Row {
                Spacer(Modifier.weight(1f))
                Button(onClick = ::save, enabled = canSave) { Text(stringResource(R.string.wants_save)) }
            }
        }
    }
}

/** The calendar for the day a need is needed by. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NeedByPicker(current: LocalDate?, onPick: (LocalDate) -> Unit, onDismiss: () -> Unit) {
    val state = rememberDatePickerState(initialSelectedDateMillis = current?.atStartOfDay(ZoneOffset.UTC)?.toInstant()?.toEpochMilli())
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = { state.selectedDateMillis?.let { onPick(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate()) } },
                enabled = state.selectedDateMillis != null,
            ) { Text(stringResource(R.string.needs_by_pick)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.needs_cancel)) } },
    ) {
        DatePicker(state = state)
    }
}
