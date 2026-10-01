package com.goalmaker.app.ui.widget

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.goalmaker.app.R
import com.goalmaker.app.application.planning.GoalHorizon
import com.goalmaker.app.ui.components.ChoiceChip
import com.goalmaker.app.ui.components.ScreenTitle
import com.goalmaker.app.ui.theme.AppTheme

/**
 * Sets up one Motivation widget: the owner's own words (several lines are fine) or the goals of this
 * week, month or year. Saving needs words in text mode; the goals are read from the replica each
 * time the widget is drawn.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MotivationConfigureScreen(initial: MotivationChoice, onSave: (MotivationChoice) -> Unit, onCancel: () -> Unit) {
    var mode by rememberSaveable { mutableStateOf(initial.mode) }
    var text by rememberSaveable { mutableStateOf(initial.text) }
    var horizon by rememberSaveable { mutableStateOf(initial.horizon) }
    val choice = MotivationChoice(mode, text, horizon)
    Scaffold(
        topBar = {
            TopAppBar(
                title = { ScreenTitle(stringResource(R.string.widget_motivation_setup_title)) },
                navigationIcon = {
                    IconButton(onClick = onCancel) {
                        Icon(Icons.Outlined.Close, contentDescription = stringResource(R.string.widget_motivation_setup_cancel))
                    }
                },
            )
        },
    ) { padding ->
        Column(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .consumeWindowInsets(padding)
                .imePadding()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            Label(stringResource(R.string.widget_motivation_setup_show))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ChoiceChip(mode == MotivationMode.TEXT, { mode = MotivationMode.TEXT }, stringResource(R.string.widget_motivation_mode_text))
                ChoiceChip(mode == MotivationMode.GOALS, { mode = MotivationMode.GOALS }, stringResource(R.string.widget_motivation_mode_goals))
            }
            if (mode == MotivationMode.TEXT) {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it.take(MotivationChoice.MAX_TEXT) },
                    label = { Text(stringResource(R.string.widget_motivation_text_label)) },
                    placeholder = { Text(stringResource(R.string.widget_motivation_text_placeholder)) },
                    supportingText = { Text(stringResource(R.string.widget_motivation_text_hint)) },
                    minLines = 3,
                    maxLines = 8,
                    modifier = Modifier.fillMaxWidth(),
                )
            } else {
                Label(stringResource(R.string.widget_motivation_setup_which))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    MotivationChoice.HORIZONS.forEach { option ->
                        ChoiceChip(horizon == option, { horizon = option }, stringResource(labelOf(option)))
                    }
                }
                Text(
                    stringResource(R.string.widget_motivation_goals_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = AppTheme.colors.textMuted,
                )
            }
            Button(onClick = { onSave(choice) }, enabled = choice.ready, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.widget_motivation_setup_save))
            }
        }
    }
}

@Composable
private fun Label(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        color = AppTheme.colors.accent,
        modifier = Modifier.semantics { heading() },
    )
}

private fun labelOf(horizon: GoalHorizon): Int = when (horizon) {
    GoalHorizon.YEAR -> R.string.widget_motivation_pick_year
    GoalHorizon.MONTH -> R.string.widget_motivation_pick_month
    else -> R.string.widget_motivation_pick_week
}
