package com.goalmaker.app.ui.tally

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MediumFlexibleTopAppBar
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.goalmaker.app.R
import com.goalmaker.app.application.planning.TallyCategory
import com.goalmaker.app.application.planning.TallyRule
import com.goalmaker.app.application.planning.TallyRules
import com.goalmaker.app.ui.components.ChoiceChip
import com.goalmaker.app.ui.components.ScreenTitle
import com.goalmaker.app.ui.lists.SectionHeader
import com.goalmaker.app.ui.nav.PlaceNavigationIcon
import com.goalmaker.app.ui.theme.AppTheme
import java.time.format.TextStyle as DayStyle

/**
 * The Tally place (docs/tally.md, M8-13): the switch and, when needed, the usage access card on top;
 * filter chips for Phone, PC and each category with time; today as one stacked bar, the week as a
 * stacked bar per day, the time per project (the PC's alone); then the owner's own categories and
 * rules, each with an add sheet.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TallyScreen(viewModel: TallyViewModel, onBack: (() -> Unit)?, actions: @Composable () -> Unit) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    var addingCategory by remember { mutableStateOf(false) }
    var addingRule by remember { mutableStateOf(false) }
    var editingCategory by remember { mutableStateOf<TallyCategory?>(null) }
    var editingRule by remember { mutableStateOf<TallyRule?>(null) }

    if (addingCategory || editingCategory != null) {
        TallyCategorySheet(
            viewModel = viewModel,
            initial = editingCategory,
            taken = state.categories.map(TallyCategory::color).toSet(),
            onDismiss = {
                addingCategory = false
                editingCategory = null
            },
        )
    }
    if (addingRule || editingRule != null) {
        TallyRuleSheet(
            viewModel = viewModel,
            initial = editingRule,
            categories = state.categories,
            onDismiss = {
                addingRule = false
                editingRule = null
            },
        )
    }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            MediumFlexibleTopAppBar(
                title = { ScreenTitle(stringResource(R.string.tally_title)) },
                navigationIcon = { PlaceNavigationIcon(onBack) },
                actions = { actions() },
                scrollBehavior = scrollBehavior,
            )
        },
    ) { padding ->
        LazyColumn(
            contentPadding = PaddingValues(horizontal = AppTheme.density.pagePadding.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(AppTheme.density.rowGap.dp),
            modifier = Modifier.fillMaxSize().padding(padding),
        ) {
            item("access") { TallyAccessCard(viewModel) }
            if (!state.loaded) return@LazyColumn
            item("filters") { Filters(state, viewModel) }
            if (state.weekMinutes == 0 && state.filter.kind == null && state.filter.category == null) {
                item("empty") {
                    Text(
                        stringResource(R.string.tally_empty),
                        style = MaterialTheme.typography.bodyLarge,
                        color = AppTheme.colors.textMuted,
                        modifier = Modifier.padding(8.dp),
                    )
                }
            } else {
                state.today?.let { today ->
                    item("h-today") { SectionHeader(stringResource(R.string.tally_today)) }
                    item("today") {
                        Panel {
                            Total(today.minutes)
                            TallyStackedBar(today.slices)
                            if (today.slices.isNotEmpty()) TallyLegend(today.slices)
                        }
                    }
                }
                item("h-week") { SectionHeader(stringResource(R.string.tally_this_week)) }
                item("week") {
                    val locale = LocalConfiguration.current.locales[0]
                    Panel {
                        Total(state.weekMinutes)
                        TallyColumns(state.week, labels = state.week.map { it.day.dayOfWeek.getDisplayName(DayStyle.SHORT, locale) })
                        if (state.weekSlices.isNotEmpty()) TallyLegend(state.weekSlices)
                    }
                }
                item("h-projects") { SectionHeader(stringResource(R.string.tally_projects)) }
                if (state.projects.isEmpty()) {
                    item("projects-none") {
                        Muted(stringResource(if (state.filter.kind == TallyRules.PHONE) R.string.tally_projects_phone else R.string.tally_projects_none))
                    }
                }
                items(state.projects, key = { "project-" + it.id }) { project -> ProjectRow(project, state.projects.first().minutes) }
            }

            item("h-categories") { SectionHeader(stringResource(R.string.tally_categories)) }
            if (state.own.isEmpty()) item("categories-none") { Muted(stringResource(R.string.tally_categories_none)) }
            items(state.own, key = { "category-" + it.id }) { category -> CategoryRow(category, onEdit = { editingCategory = category }) }
            item("add-category") { AddButton(stringResource(R.string.tally_category_add)) { addingCategory = true } }

            item("h-rules") { SectionHeader(stringResource(R.string.tally_rules)) }
            if (state.rules.isEmpty()) item("rules-none") { Muted(stringResource(R.string.tally_rules_none)) }
            items(state.rules, key = { "rule-" + (it.id ?: it.pattern) }) { rule -> RuleRow(rule, state, onEdit = { editingRule = rule }) }
            item("add-rule") { AddButton(stringResource(R.string.tally_rule_add)) { addingRule = true } }
        }
    }
}

/** Phone and PC, then a chip per category with time this week; picking one again lets it go. */
@Composable
private fun Filters(state: TallyUiState, viewModel: TallyViewModel) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(vertical = 4.dp),
    ) {
        ChoiceChip(
            selected = state.filter.kind == TallyRules.PHONE,
            onClick = { viewModel.showKind(TallyRules.PHONE) },
            label = stringResource(R.string.tally_phone),
        )
        ChoiceChip(
            selected = state.filter.kind == TallyRules.PC,
            onClick = { viewModel.showKind(TallyRules.PC) },
            label = stringResource(R.string.tally_pc),
        )
        state.chips.forEach { chip ->
            ChoiceChip(
                selected = state.filter.category == chip.category,
                onClick = { viewModel.showCategory(chip.category) },
                label = sliceName(chip),
            )
        }
    }
}

@Composable
private fun Panel(content: @Composable () -> Unit) {
    Column(
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier
            .fillMaxWidth()
            .background(AppTheme.colors.surface, AppTheme.shapes.card)
            .padding(12.dp),
    ) { content() }
}

/** The minutes in all, as a big number. */
@Composable
private fun Total(minutes: Int) {
    Text(durationText(minutes), style = AppTheme.type.number.copy(fontSize = 28.sp), color = AppTheme.colors.text)
}

/** A project with its minutes and a thin bar against the project with the most. */
@Composable
private fun ProjectRow(project: TallyProjectTime, most: Int) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(AppTheme.colors.surface, AppTheme.shapes.row)
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                project.name.ifBlank { stringResource(R.string.tally_removed_project) },
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Text(durationText(project.minutes), style = tabular(MaterialTheme.typography.titleSmall), color = AppTheme.colors.accent)
        }
        Box(
            Modifier
                .padding(top = 6.dp)
                .fillMaxWidth()
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(AppTheme.colors.outline.copy(alpha = 0.25f)),
        ) {
            Box(
                Modifier
                    .fillMaxWidth(project.minutes.toFloat() / most.coerceAtLeast(1))
                    .height(6.dp)
                    .background(AppTheme.colors.accent),
            )
        }
    }
}

/** One of the owner's categories; a tap opens it to edit or delete. */
@Composable
private fun CategoryRow(category: TallyCategory, onEdit: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = AppTheme.density.rowMinHeight.dp)
            .clip(AppTheme.shapes.row)
            .background(AppTheme.colors.surface)
            .clickable(onClickLabel = stringResource(R.string.tally_edit), onClick = onEdit)
            .padding(horizontal = 16.dp),
    ) {
        Box(Modifier.size(14.dp).background(tallyColor(category.color), CircleShape))
        Text(
            listOfNotNull(category.emoji, category.name).joinToString(" "),
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f).padding(start = 12.dp),
        )
        EditMark()
    }
}

/** A rule as it reads: what it matches, the pattern, where, and the category it sorts into. A tap opens it. */
@Composable
private fun RuleRow(rule: TallyRule, state: TallyUiState, onEdit: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = AppTheme.density.rowMinHeight.dp)
            .clip(AppTheme.shapes.row)
            .background(AppTheme.colors.surface)
            .clickable(onClickLabel = stringResource(R.string.tally_edit), onClick = onEdit)
            .padding(horizontal = 16.dp, vertical = 10.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                stringResource(R.string.tally_rule_line, rule.pattern, state.nameOf(rule.category) ?: stringResource(R.string.tally_removed_category)),
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                listOf(stringResource(matchLabel(rule.match)), stringResource(platformLabel(rule.platform))).joinToString(" · "),
                style = MaterialTheme.typography.bodyMedium,
                color = AppTheme.colors.textMuted,
            )
        }
        EditMark()
    }
}

/** The pencil at a row's end that says a tap edits it; the row itself carries the action. */
@Composable
private fun EditMark() {
    Icon(Icons.Outlined.Edit, contentDescription = null, tint = AppTheme.colors.textMuted, modifier = Modifier.padding(start = 8.dp).size(18.dp))
}

@Composable
private fun AddButton(label: String, onClick: () -> Unit) {
    OutlinedButton(onClick = onClick) {
        Icon(Icons.Outlined.Add, contentDescription = null, modifier = Modifier.size(18.dp))
        Text(label, modifier = Modifier.padding(start = 6.dp))
    }
}

@Composable
private fun Muted(text: String) {
    Text(text, style = MaterialTheme.typography.bodyMedium, color = AppTheme.colors.textMuted, modifier = Modifier.padding(horizontal = 4.dp))
}
