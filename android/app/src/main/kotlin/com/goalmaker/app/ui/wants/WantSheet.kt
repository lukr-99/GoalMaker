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
import kotlinx.coroutines.launch

/**
 * Adds a want or edits one (docs/wants.md). The reason is required; a new want shows the cooldown
 * its price gives, and the owner can pick another number of days before saving. Editing never
 * changes the cooldown already set.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun WantSheet(viewModel: WantsViewModel, initial: WantItem?, initialTitle: String, onDismiss: () -> Unit) {
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    var title by rememberSaveable { mutableStateOf(initialTitle) }
    var reason by rememberSaveable { mutableStateOf(initial?.reason.orEmpty()) }
    var priceText by rememberSaveable { mutableStateOf(initial?.price?.let { if (it % 1.0 == 0.0) it.toLong().toString() else it.toString() }.orEmpty()) }
    var currency by rememberSaveable { mutableStateOf(initial?.currency ?: viewModel.uiState.value.cooldowns.currency) }
    var link by rememberSaveable { mutableStateOf(initial?.link.orEmpty()) }
    var picked by rememberSaveable { mutableStateOf<Int?>(null) }
    val price = WantMoney.parse(priceText)
    val days = viewModel.cooldownFor(price, currency.trim().uppercase(), picked)
    val canSave = title.isNotBlank() && reason.isNotBlank()

    fun save() {
        if (!canSave) return
        val draft = WantDraft(
            title = title,
            reason = reason,
            link = link,
            price = price,
            currency = currency,
            pickedDays = picked,
        )
        scope.launch {
            val saved = if (initial == null) viewModel.add(draft) != null else viewModel.update(initial.id, draft)
            if (saved) sheet.hide()
            onDismiss()
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
            ScreenTitle(stringResource(if (initial == null) R.string.wants_add else R.string.wants_edit))
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
                label = { Text(stringResource(R.string.wants_reason_field)) },
                supportingText = { Text(stringResource(R.string.wants_reason_hint)) },
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
            if (initial == null) {
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
