package com.goalmaker.app.ui.wants

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MediumFlexibleTopAppBar
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.goalmaker.app.R
import com.goalmaker.app.application.planning.WantItem
import com.goalmaker.app.application.planning.WantRules
import com.goalmaker.app.application.planning.WantState
import com.goalmaker.app.ui.components.AppSnackbarHost
import com.goalmaker.app.ui.components.ChoiceChip
import com.goalmaker.app.ui.components.ProgressRing
import com.goalmaker.app.ui.components.ScreenTitle
import com.goalmaker.app.ui.nav.PlaceNavigationIcon
import com.goalmaker.app.ui.theme.AppTheme

/**
 * The Wants place (docs/wants.md, M8-04): the thresholds on top, filter chips for Ready, Cooling and
 * Decided, and a row per want with a ring counting its cooldown down. A row opens to decide.
 * [addTitle] opens the add sheet with that title (from `/want` in the composer).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WantsScreen(
    viewModel: WantsViewModel,
    onBack: (() -> Unit)?,
    actions: @Composable () -> Unit,
    addTitle: String? = null,
    onAddShown: () -> Unit = {},
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val snackbars = remember { SnackbarHostState() }
    val resources = LocalResources.current
    var editing by remember { mutableStateOf<WantItem?>(null) }
    var adding by remember { mutableStateOf<String?>(null) }
    var editingCooldowns by remember { mutableStateOf(false) }
    var open by rememberSaveable { mutableStateOf<String?>(null) }

    LaunchedEffect(addTitle) {
        if (addTitle != null) {
            adding = addTitle
            onAddShown()
        }
    }
    LaunchedEffect(viewModel) {
        viewModel.undo.collect { event ->
            val message = resources.getString(
                when (event.kind) {
                    WantUndo.Kind.BOUGHT -> R.string.wants_bought_message
                    WantUndo.Kind.DROPPED -> R.string.wants_dropped_message
                    WantUndo.Kind.DELETED -> R.string.wants_deleted_message
                },
                event.title,
            )
            val result = snackbars.showSnackbar(message, actionLabel = resources.getString(R.string.lists_undo), duration = SnackbarDuration.Short)
            if (result == SnackbarResult.ActionPerformed) event.undo()
        }
    }

    adding?.let { title ->
        WantSheet(
            viewModel = viewModel,
            initial = null,
            initialTitle = title,
            onDismiss = { adding = null },
        )
    }
    editing?.let { want ->
        WantSheet(viewModel = viewModel, initial = want, initialTitle = want.title, onDismiss = { editing = null })
    }
    if (editingCooldowns) {
        CooldownsSheet(viewModel = viewModel, current = state.cooldowns, onDismiss = { editingCooldowns = false })
    }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        snackbarHost = { AppSnackbarHost(snackbars) },
        topBar = {
            MediumFlexibleTopAppBar(
                title = { ScreenTitle(stringResource(R.string.wants_title)) },
                navigationIcon = { PlaceNavigationIcon(onBack) },
                actions = {
                    IconButton(onClick = { adding = "" }) {
                        Icon(Icons.Outlined.Add, contentDescription = stringResource(R.string.wants_add))
                    }
                    actions()
                },
                scrollBehavior = scrollBehavior,
            )
        },
    ) { padding ->
        if (!state.loaded) return@Scaffold
        LazyColumn(
            contentPadding = PaddingValues(horizontal = AppTheme.density.pagePadding.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(AppTheme.density.rowGap.dp),
            modifier = Modifier.fillMaxSize().padding(padding),
        ) {
            item("cooldowns") { CooldownsLine(state, onEdit = { editingCooldowns = true }) }
            item("filters") {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(vertical = 4.dp),
                ) {
                    listOf(WantState.READY to R.string.wants_ready, WantState.COOLING to R.string.wants_cooling, WantState.DECIDED to R.string.wants_decided)
                        .forEach { (filter, label) ->
                            val count = state.counts[filter] ?: 0
                            ChoiceChip(
                                selected = state.filter == filter,
                                onClick = { viewModel.show(filter) },
                                label = if (count > 0) "${stringResource(label)} $count" else stringResource(label),
                            )
                        }
                }
            }
            if (state.rows.isEmpty()) {
                item("empty") {
                    Text(
                        stringResource(
                            when {
                                state.empty -> R.string.wants_empty
                                state.filter == WantState.READY -> R.string.wants_none_ready
                                state.filter == WantState.COOLING -> R.string.wants_none_cooling
                                else -> R.string.wants_none_decided
                            },
                        ),
                        style = MaterialTheme.typography.bodyLarge,
                        color = AppTheme.colors.textMuted,
                        modifier = Modifier.padding(8.dp),
                    )
                }
            }
            items(state.rows, key = { it.want.id }) { row ->
                WantCard(
                    row = row,
                    expanded = open == row.want.id,
                    onToggle = { open = if (open == row.want.id) null else row.want.id },
                    onDecide = { decision, note ->
                        open = null
                        viewModel.decide(row.want, decision, note)
                    },
                    onReopen = { viewModel.reopen(row.want) },
                    onEdit = { editing = row.want },
                    onDelete = {
                        open = null
                        viewModel.delete(row.want)
                    },
                    modifier = Modifier.animateItem(),
                )
            }
        }
    }
}

/** The thresholds in one line, with the button that edits them. */
@Composable
private fun CooldownsLine(state: WantsUiState, onEdit: () -> Unit) {
    val locale = LocalConfiguration.current.locales[0]
    val c = state.cooldowns
    val line = stringResource(
        R.string.wants_cooldowns_line,
        WantMoney.format(c.smallUnder, c.currency, locale),
        c.smallDays,
        WantMoney.format(c.mediumUnder, c.currency, locale),
        c.mediumDays,
        c.largeDays,
        c.unpricedDays,
    )
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Text(line, style = MaterialTheme.typography.bodySmall, color = AppTheme.colors.textMuted, modifier = Modifier.weight(1f))
        IconButton(onClick = onEdit) {
            Icon(Icons.Outlined.Tune, contentDescription = stringResource(R.string.wants_cooldowns_edit), tint = AppTheme.colors.accent)
        }
    }
}

@Composable
private fun WantCard(
    row: WantRow,
    expanded: Boolean,
    onToggle: () -> Unit,
    onDecide: (String, String) -> Unit,
    onReopen: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val want = row.want
    val colors = AppTheme.colors
    val locale = LocalConfiguration.current.locales[0]
    val uri = LocalUriHandler.current
    var note by rememberSaveable(want.id) { mutableStateOf("") }
    val status = when (row.state) {
        WantState.READY -> stringResource(R.string.wants_status_ready)
        WantState.COOLING -> pluralStringResource(R.plurals.wants_status_days, row.daysLeft, row.daysLeft)
        WantState.DECIDED -> stringResource(if (want.decision == WantRules.BOUGHT) R.string.wants_status_bought else R.string.wants_status_dropped)
    }
    val price = want.price?.let { WantMoney.format(it, want.currency, locale) }

    Surface(
        shape = AppTheme.shapes.row,
        color = colors.surface,
        border = if (row.state == WantState.READY) BorderStroke(1.dp, colors.accent.copy(alpha = 0.6f)) else null,
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(Modifier.clickable(onClick = onToggle).padding(AppTheme.density.cardPadding.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.heightIn(min = 44.dp)) {
                CooldownRing(row)
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        want.title,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        listOfNotNull(price, status).joinToString(" · "),
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (row.state == WantState.READY) colors.accent else colors.textMuted,
                    )
                }
            }
            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically(tween(AppTheme.motion.standard)) + fadeIn(tween(AppTheme.motion.standard)),
                exit = shrinkVertically(tween(AppTheme.motion.quick)) + fadeOut(tween(AppTheme.motion.quick)),
            ) {
                Column(Modifier.padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Labeled(stringResource(R.string.wants_reason), want.reason)
                    want.link?.let { link ->
                        Text(
                            link,
                            style = MaterialTheme.typography.bodyMedium,
                            color = colors.accent,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.clickable { runCatching { uri.openUri(link) } },
                        )
                    }
                    want.checkedPrice?.let { checked ->
                        Labeled(
                            stringResource(R.string.wants_checked),
                            listOf(WantMoney.format(checked, want.currency, locale), want.checkedNote).filter(String::isNotBlank).joinToString(" · "),
                        )
                    }
                    if (want.decision != null && want.decisionNote.isNotBlank()) Labeled(stringResource(R.string.wants_note), want.decisionNote)
                    if (row.state == WantState.DECIDED) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(onClick = onReopen) { Text(stringResource(R.string.wants_reopen)) }
                            TextButton(onClick = onDelete) { Text(stringResource(R.string.wants_delete), color = colors.danger) }
                        }
                    } else {
                        OutlinedTextField(
                            value = note,
                            onValueChange = { note = it.take(2000) },
                            label = { Text(stringResource(R.string.wants_note_optional)) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            FilledTonalButton(onClick = { onDecide(WantRules.BOUGHT, note) }) {
                                Icon(Icons.Outlined.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text(stringResource(R.string.wants_buy))
                            }
                            OutlinedButton(onClick = { onDecide(WantRules.DROPPED, note) }) { Text(stringResource(R.string.wants_drop)) }
                            Spacer(Modifier.weight(1f))
                            TextButton(onClick = onEdit) { Text(stringResource(R.string.wants_edit)) }
                        }
                    }
                }
            }
        }
    }
}

/** The ring counting a cooldown down; full and a little pop once it is ready. */
@Composable
private fun CooldownRing(row: WantRow) {
    val reduced = AppTheme.reduceMotion
    val quick = AppTheme.motion.quick
    val pop = remember(row.want.id) { Animatable(1f) }
    LaunchedEffect(row.want.id, row.state) {
        if (row.state == WantState.READY && !reduced) {
            pop.animateTo(1.12f, tween(quick))
            pop.animateTo(1f, spring(dampingRatio = 0.4f, stiffness = 400f))
        }
    }
    val description = stringResource(R.string.wants_ring, (row.progress * 100).toInt())
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .graphicsLayer { scaleX = pop.value; scaleY = pop.value }
            .semantics { contentDescription = description },
    ) {
        ProgressRing(fraction = row.progress.toFloat(), size = 40.dp, stroke = 5.dp)
        when (row.state) {
            WantState.COOLING -> Text(
                row.daysLeft.toString(),
                style = AppTheme.type.number.copy(fontSize = MaterialTheme.typography.labelMedium.fontSize),
                color = AppTheme.colors.text,
            )
            else -> Icon(Icons.Outlined.Check, contentDescription = null, tint = AppTheme.colors.accent, modifier = Modifier.size(18.dp))
        }
    }
}

@Composable
private fun Labeled(label: String, text: String) {
    Column {
        Text(label.uppercase(), style = MaterialTheme.typography.labelSmall, color = AppTheme.colors.accent, fontWeight = FontWeight.Bold)
        Text(text, style = MaterialTheme.typography.bodyMedium, color = AppTheme.colors.text)
    }
}
