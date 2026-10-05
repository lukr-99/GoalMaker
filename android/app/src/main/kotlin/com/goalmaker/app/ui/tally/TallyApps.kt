package com.goalmaker.app.ui.tally

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.runtime.remember
import com.goalmaker.app.application.planning.TallyCategory
import com.goalmaker.app.application.planning.TallyRules
import com.goalmaker.app.R
import com.goalmaker.app.ui.components.ChoiceChip
import com.goalmaker.app.ui.theme.AppTheme

/**
 * This phone's apps in the Tally place (docs/tally.md, ADR 0013): for the day shown or the week, a row
 * per category with its minutes; a tap opens it to the apps that made it up, each with Make a rule,
 * which starts a rule that sorts the app into another category. Only this phone's own apps are here,
 * since the record of apps never leaves the device; the note under the chips says so.
 */
@Composable
internal fun TallyAppsPanel(
    state: TallyUiState,
    dayLabel: String,
    onScope: (TallyAppScope) -> Unit,
    onMakeRule: (group: TallyAppGroup, app: TallyAppRow) -> Unit,
    onMove: (app: String, category: String) -> Unit,
) {
    var open by rememberSaveable { mutableStateOf(listOf<String>()) }
    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier
            .fillMaxWidth()
            .background(AppTheme.colors.surface, AppTheme.shapes.card)
            .padding(12.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ChoiceChip(selected = state.appScope == TallyAppScope.DAY, onClick = { onScope(TallyAppScope.DAY) }, label = dayLabel)
            ChoiceChip(selected = state.appScope == TallyAppScope.WEEK, onClick = { onScope(TallyAppScope.WEEK) }, label = stringResource(R.string.tally_this_week))
        }
        Text(stringResource(R.string.tally_apps_only_here), style = MaterialTheme.typography.bodySmall, color = AppTheme.colors.textMuted)
        if (state.apps.isEmpty()) {
            Text(stringResource(R.string.tally_apps_none), style = MaterialTheme.typography.bodyMedium, color = AppTheme.colors.textMuted)
        } else {
            ToSort(state, onMove)
        }
        state.apps.forEach { group ->
            val expanded = group.category in open
            CategoryLine(group, expanded) { open = if (expanded) open - group.category else open + group.category }
            if (expanded) {
                group.apps.forEach { app ->
                    AppLine(app, state.categories, group.category, onMove = { onMove(app.app, it) }) { onMakeRule(group, app) }
                }
            }
        }
    }
}

/** A category with its minutes; a tap shows or hides its apps. */
@Composable
private fun CategoryLine(group: TallyAppGroup, expanded: Boolean, onToggle: () -> Unit) {
    val name = group.name.ifBlank { stringResource(R.string.tally_removed_category) }
    val state = stringResource(if (expanded) R.string.tally_apps_shown else R.string.tally_apps_hidden)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = AppTheme.density.rowMinHeight.dp)
            .clip(AppTheme.shapes.row)
            .clickable(onClickLabel = stringResource(if (expanded) R.string.tally_apps_close else R.string.tally_apps_open), onClick = onToggle)
            .semantics { stateDescription = state }
            .padding(horizontal = 4.dp),
    ) {
        Box(Modifier.size(12.dp).background(tallyColor(group.color), CircleShape))
        Text(
            listOfNotNull(group.emoji, name).joinToString(" "),
            style = MaterialTheme.typography.bodyLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f).padding(start = 10.dp),
        )
        Text(durationText(group.minutes), style = tabular(MaterialTheme.typography.titleSmall), color = AppTheme.colors.accent)
        Icon(
            if (expanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
            contentDescription = null,
            tint = AppTheme.colors.textMuted,
            modifier = Modifier.padding(start = 6.dp),
        )
    }
}

/**
 * What landed in Other, most first, each with its categories one tap away (docs/tally.md, "Sorting"): a
 * tap saves the rule and counts today again, so the app leaves the list.
 */
@Composable
private fun ToSort(state: TallyUiState, onMove: (app: String, category: String) -> Unit) {
    Text(
        stringResource(R.string.tally_to_sort).uppercase(),
        style = MaterialTheme.typography.labelSmall,
        color = AppTheme.colors.accent,
        modifier = Modifier.padding(top = 4.dp),
    )
    if (state.toSort.isEmpty()) {
        Text(stringResource(R.string.tally_to_sort_none), style = MaterialTheme.typography.bodySmall, color = AppTheme.colors.textMuted)
        return
    }
    state.toSort.forEach { app ->
        Column(Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(app.name, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                Text(durationText(app.minutes), style = tabular(MaterialTheme.typography.bodyMedium), color = AppTheme.colors.text)
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(top = 4.dp, bottom = 6.dp),
            ) {
                state.categories.filter { it.id != TallyRules.OTHER }.forEach { category ->
                    ChoiceChip(
                        selected = false,
                        onClick = { onMove(app.app, category.id) },
                        label = listOfNotNull(category.emoji, category.name).joinToString(" "),
                    )
                }
            }
        }
    }
}

/** One app: its name, its package when the name differs, its minutes, its sites, Move to and Make a rule. */
@Composable
private fun AppLine(app: TallyAppRow, categories: List<TallyCategory>, current: String, onMove: (String) -> Unit, onMakeRule: () -> Unit) {
    val makeRule = stringResource(R.string.tally_make_rule_for, app.name)
    Column(Modifier.fillMaxWidth().padding(start = 22.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(app.name, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (app.name != app.app) {
                    Text(app.app, style = MaterialTheme.typography.bodySmall, color = AppTheme.colors.textMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            Text(durationText(app.minutes), style = tabular(MaterialTheme.typography.bodyMedium), color = AppTheme.colors.text)
        }
        app.windows.forEach { window ->
            Row(Modifier.padding(start = 12.dp, top = 2.dp)) {
                Text(window.label, style = MaterialTheme.typography.bodySmall, color = AppTheme.colors.textMuted, modifier = Modifier.weight(1f))
                Text(durationText(window.minutes), style = tabular(MaterialTheme.typography.bodySmall), color = AppTheme.colors.textMuted)
            }
        }
        Row {
            var moving by remember { mutableStateOf(false) }
            Box {
                TextButton(onClick = { moving = true }) { Text(stringResource(R.string.tally_move_to)) }
                DropdownMenu(expanded = moving, onDismissRequest = { moving = false }) {
                    categories.filter { it.id != current }.forEach { category ->
                        DropdownMenuItem(
                            text = { Text(listOfNotNull(category.emoji, category.name).joinToString(" ")) },
                            onClick = {
                                moving = false
                                onMove(category.id)
                            },
                        )
                    }
                }
            }
            TextButton(onClick = onMakeRule, modifier = Modifier.semantics { contentDescription = makeRule }) {
                Text(stringResource(R.string.tally_make_rule))
            }
        }
    }
}
