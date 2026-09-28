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
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.goalmaker.app.R
import com.goalmaker.app.application.planning.WantCooldowns
import com.goalmaker.app.ui.components.ScreenTitle
import com.goalmaker.app.ui.theme.AppTheme
import kotlinx.coroutines.launch

/** The owner's cooldown thresholds; the wants already cooling keep their days. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun CooldownsSheet(viewModel: WantsViewModel, current: WantCooldowns, onDismiss: () -> Unit) {
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    fun amount(value: Double) = if (value % 1.0 == 0.0) value.toLong().toString() else value.toString()
    var smallUnder by rememberSaveable { mutableStateOf(amount(current.smallUnder)) }
    var smallDays by rememberSaveable { mutableStateOf(current.smallDays.toString()) }
    var mediumUnder by rememberSaveable { mutableStateOf(amount(current.mediumUnder)) }
    var mediumDays by rememberSaveable { mutableStateOf(current.mediumDays.toString()) }
    var largeDays by rememberSaveable { mutableStateOf(current.largeDays.toString()) }
    var unpricedDays by rememberSaveable { mutableStateOf(current.unpricedDays.toString()) }
    var currency by rememberSaveable { mutableStateOf(current.currency) }
    var refused by rememberSaveable { mutableStateOf(false) }

    val candidate = runCatching {
        WantCooldowns(
            smallUnder = WantMoney.parse(smallUnder)!!,
            smallDays = smallDays.trim().toInt(),
            mediumUnder = WantMoney.parse(mediumUnder)!!,
            mediumDays = mediumDays.trim().toInt(),
            largeDays = largeDays.trim().toInt(),
            unpricedDays = unpricedDays.trim().toInt(),
            currency = currency.trim().uppercase(),
        )
    }.getOrNull()

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
            ScreenTitle(stringResource(R.string.wants_cooldowns_title))
            Text(stringResource(R.string.wants_cooldowns_hint), style = MaterialTheme.typography.bodyMedium, color = AppTheme.colors.textMuted)
            Pair(R.string.wants_under, R.string.wants_wait_days).let { (under, wait) ->
                Band(stringResource(under), smallUnder, { smallUnder = it }, stringResource(wait), smallDays, { smallDays = it })
                Band(stringResource(under), mediumUnder, { mediumUnder = it }, stringResource(wait), mediumDays, { mediumDays = it })
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                NumberField(stringResource(R.string.wants_more_wait), largeDays, { largeDays = it }, Modifier.weight(1f))
                NumberField(stringResource(R.string.wants_unpriced_wait), unpricedDays, { unpricedDays = it }, Modifier.weight(1f))
            }
            OutlinedTextField(
                value = currency,
                onValueChange = { currency = it.take(3).uppercase() },
                label = { Text(stringResource(R.string.wants_currency)) },
                singleLine = true,
                modifier = Modifier.width(120.dp),
            )
            if (refused) {
                Text(stringResource(R.string.wants_cooldowns_refused), style = MaterialTheme.typography.bodyMedium, color = AppTheme.colors.danger)
            }
            Row {
                TextButton(onClick = {
                    val defaults = WantCooldowns.DEFAULT
                    smallUnder = amount(defaults.smallUnder); smallDays = defaults.smallDays.toString()
                    mediumUnder = amount(defaults.mediumUnder); mediumDays = defaults.mediumDays.toString()
                    largeDays = defaults.largeDays.toString(); unpricedDays = defaults.unpricedDays.toString()
                    currency = defaults.currency
                }) { Text(stringResource(R.string.wants_cooldowns_defaults)) }
                Spacer(Modifier.weight(1f))
                Button(
                    enabled = candidate != null,
                    onClick = {
                        val value = candidate ?: return@Button
                        scope.launch {
                            if (viewModel.setCooldowns(value)) {
                                sheet.hide()
                                onDismiss()
                            } else {
                                refused = true
                            }
                        }
                    },
                ) { Text(stringResource(R.string.wants_save)) }
            }
        }
    }
}

@Composable
private fun Band(underLabel: String, under: String, onUnder: (String) -> Unit, waitLabel: String, wait: String, onWait: (String) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        NumberField(underLabel, under, onUnder, Modifier.weight(1f), decimal = true)
        NumberField(waitLabel, wait, onWait, Modifier.weight(1f))
    }
}

@Composable
private fun NumberField(label: String, value: String, onChange: (String) -> Unit, modifier: Modifier, decimal: Boolean = false) {
    OutlinedTextField(
        value = value,
        onValueChange = { onChange(it.take(12)) },
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = if (decimal) KeyboardType.Decimal else KeyboardType.Number),
        modifier = modifier,
    )
}
