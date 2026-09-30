package com.goalmaker.app.ui.tally

import androidx.annotation.StringRes
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import com.goalmaker.app.R
import com.goalmaker.app.application.planning.TallyCategory
import com.goalmaker.app.application.planning.TallyRule
import com.goalmaker.app.application.planning.TallyRules
import com.goalmaker.app.ui.components.ChoiceChip
import com.goalmaker.app.ui.components.ScreenTitle
import com.goalmaker.app.ui.theme.AppTheme
import kotlinx.coroutines.launch

/**
 * Adds a Tally rule of the owner's own, or edits or deletes [initial] (docs/tally.md): what it matches (an app, a window title or an
 * editor's folder), the pattern, where it applies and the category it sorts into. Titles and folders
 * exist only on the PC, so those rules can't be for Android alone. The owner's rules come before the
 * shipped ones, so the next count sorts by it.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
internal fun TallyRuleSheet(viewModel: TallyViewModel, initial: TallyRule?, categories: List<TallyCategory>, onDismiss: () -> Unit) {
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    var match by rememberSaveable { mutableStateOf(initial?.match ?: TallyRules.APP) }
    var pattern by rememberSaveable { mutableStateOf(initial?.pattern.orEmpty()) }
    var platform by rememberSaveable { mutableStateOf(initial?.platform ?: TallyRules.ANY) }
    var category by rememberSaveable { mutableStateOf(initial?.category) }
    val canSave = pattern.isNotBlank() && category != null

    fun save() {
        val chosen = category ?: return
        if (!canSave) return
        scope.launch {
            // Editing keeps the rule's project, which the PC may have set.
            val rule = TallyRule(match, pattern, platform, chosen, project = initial?.project)
            val id = initial?.id
            val saved = if (id == null) viewModel.addRule(rule) != null else viewModel.updateRule(id, rule)
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
            ScreenTitle(stringResource(if (initial == null) R.string.tally_rule_add else R.string.tally_rule_edit))
            Text(stringResource(R.string.tally_rule_match), style = MaterialTheme.typography.titleSmall)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(TallyRules.APP, TallyRules.TITLE, TallyRules.FOLDER).forEach { value ->
                    ChoiceChip(
                        selected = match == value,
                        onClick = {
                            match = value
                            if (value != TallyRules.APP && platform == TallyRules.ANDROID) platform = TallyRules.WINDOWS
                        },
                        label = stringResource(matchLabel(value)),
                    )
                }
            }
            OutlinedTextField(
                value = pattern,
                onValueChange = { pattern = it.take(200) },
                label = { Text(stringResource(R.string.tally_rule_pattern)) },
                supportingText = { Text(stringResource(patternHint(match))) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.None),
                modifier = Modifier.fillMaxWidth(),
            )
            Text(stringResource(R.string.tally_rule_platform), style = MaterialTheme.typography.titleSmall)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                // Titles and folders exist only on the PC.
                listOf(TallyRules.ANY, TallyRules.ANDROID, TallyRules.WINDOWS)
                    .filter { it != TallyRules.ANDROID || match == TallyRules.APP }
                    .forEach { value ->
                        ChoiceChip(selected = platform == value, onClick = { platform = value }, label = stringResource(platformLabel(value)))
                    }
            }
            Text(stringResource(R.string.tally_rule_category), style = MaterialTheme.typography.titleSmall)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                categories.forEach { option ->
                    ChoiceChip(
                        selected = category == option.id,
                        onClick = { category = option.id },
                        label = listOfNotNull(option.emoji, option.name).joinToString(" "),
                    )
                }
            }
            Row {
                initial?.id?.let { id ->
                    TextButton(onClick = {
                        viewModel.deleteRule(id)
                        onDismiss()
                    }) { Text(stringResource(R.string.tally_delete), color = AppTheme.colors.danger) }
                }
                Spacer(Modifier.weight(1f))
                Button(onClick = ::save, enabled = canSave) { Text(stringResource(R.string.tally_save)) }
            }
        }
    }
}

/** What a rule matches, as the sheet and the rule list name it. */
@StringRes
internal fun matchLabel(match: String): Int = when (match) {
    TallyRules.TITLE -> R.string.tally_match_title
    TallyRules.FOLDER -> R.string.tally_match_folder
    else -> R.string.tally_match_app
}

/** Where a rule applies, as the sheet and the rule list name it. */
@StringRes
internal fun platformLabel(platform: String): Int = when (platform) {
    TallyRules.ANDROID -> R.string.tally_platform_android
    TallyRules.WINDOWS -> R.string.tally_platform_windows
    else -> R.string.tally_platform_any
}

@StringRes
private fun patternHint(match: String): Int = when (match) {
    TallyRules.TITLE -> R.string.tally_pattern_title_hint
    TallyRules.FOLDER -> R.string.tally_pattern_folder_hint
    else -> R.string.tally_pattern_app_hint
}
