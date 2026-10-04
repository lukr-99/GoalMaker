package com.goalmaker.app.ui.places

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Email
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MediumFlexibleTopAppBar
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.goalmaker.app.R
import com.goalmaker.app.domain.navigation.DeviceKind
import com.goalmaker.app.domain.navigation.PlaceRules
import com.goalmaker.app.ui.components.ProgressRing
import com.goalmaker.app.ui.components.ScreenTitle
import com.goalmaker.app.ui.nav.AppMark
import com.goalmaker.app.ui.nav.PlaceLook
import com.goalmaker.app.ui.tally.TallySlice
import com.goalmaker.app.ui.tally.TallyStackedBar
import com.goalmaker.app.ui.tally.durationText
import com.goalmaker.app.ui.theme.AppTheme
import kotlinx.coroutines.delay

/**
 * The Places hub (ADR 0014, docs/design/spec.md, Navigation): a live tile for every place, the
 * pinned ones marked, and Edit, where a tap pins or unpins a place. A fifth pin is refused, so at four
 * the unpinned tiles fade until one is unpinned.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlacesScreen(viewModel: PlacesViewModel, onOpen: (String) -> Unit, actions: @Composable () -> Unit) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val haptics = LocalHapticFeedback.current
    val digest = state.digest

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            MediumFlexibleTopAppBar(
                title = { ScreenTitle(stringResource(R.string.places_title)) },
                navigationIcon = { AppMark() },
                actions = { actions() },
                scrollBehavior = scrollBehavior,
            )
        },
    ) { padding ->
        if (digest == null) return@Scaffold
        val gap = AppTheme.density.rowGap.dp + 2.dp
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            contentPadding = PaddingValues(horizontal = AppTheme.density.pagePadding.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(gap),
            verticalArrangement = Arrangement.spacedBy(gap),
            modifier = Modifier.fillMaxSize().padding(padding),
        ) {
            item(key = "hint", span = { GridItemSpan(maxLineSpan) }) {
                PinHint(state, onToggle = viewModel::toggleEditing)
            }
            itemsIndexed(
                PlaceRules.PLACES,
                key = { _, place -> place },
                span = { _, place -> GridItemSpan(if (place == PlaceRules.REVIEWS && digest.letterWaiting) maxLineSpan else 1) },
            ) { index, place ->
                val pinned = place in state.pins
                PlaceTile(
                    place = place,
                    digest = digest,
                    index = index,
                    pinned = pinned,
                    editing = state.editing,
                    faded = state.editing && state.full && !pinned,
                    onClick = {
                        if (state.editing) {
                            if (viewModel.togglePin(place)) haptics.performHapticFeedback(HapticFeedbackType.ToggleOn)
                            else haptics.performHapticFeedback(HapticFeedbackType.Reject)
                        } else {
                            onOpen(place)
                        }
                    },
                )
            }
        }
    }
}

/** What is in the bar, or while editing how many pins are left, with Edit or Done on the same line. */
@Composable
private fun PinHint(state: PlacesUiState, onToggle: () -> Unit) {
    val limit = PlaceRules.limit(DeviceKind.PHONE) ?: 0
    val accent = AppTheme.colors.accent
    val names = state.pins.map { stringResource(PlaceLook.title(it)) }.joinToString(", ")
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
        val text = if (state.editing) {
            val count = stringResource(R.string.places_pinned_count, state.pins.size, limit)
            val next = stringResource(if (state.full) R.string.places_unpin_first else R.string.places_tap_to_pin)
            buildAnnotatedString {
                withStyle(SpanStyle(color = accent, fontWeight = FontWeight.Bold)) { append(count) }
                append(" ")
                append(next)
            }
        } else {
            val line = stringResource(R.string.places_in_bar, names)
            buildAnnotatedString {
                append(line.substringBefore(names))
                withStyle(SpanStyle(color = accent, fontWeight = FontWeight.Bold)) { append(names) }
            }
        }
        Text(text, style = MaterialTheme.typography.bodyMedium, color = AppTheme.colors.textMuted, modifier = Modifier.weight(1f))
        val editLabel = stringResource(R.string.places_edit_label)
        TextButton(
            onClick = onToggle,
            modifier = Modifier.semantics { if (!state.editing) contentDescription = editLabel },
        ) {
            Icon(
                if (state.editing) Icons.Outlined.Check else Icons.Outlined.PushPin,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = accent,
            )
            Spacer(Modifier.width(6.dp))
            Text(stringResource(if (state.editing) R.string.places_done else R.string.places_edit), color = accent)
        }
    }
}

@Composable
private fun PlaceTile(
    place: String,
    digest: PlacesDigest,
    index: Int,
    pinned: Boolean,
    editing: Boolean,
    faded: Boolean,
    onClick: () -> Unit,
) {
    val colors = AppTheme.colors
    val motion = AppTheme.motion
    val reduced = AppTheme.reduceMotion
    val hero = place == PlaceRules.REVIEWS && digest.letterWaiting
    val container = if (hero) colors.hero else colors.surface
    val content = if (hero) colors.onHero else colors.text
    val iconTint = if (hero) colors.heroAccent else colors.accent

    // Tiles grow in one after another on the emphasized spring; reduce motion keeps a short fade.
    val entrance = remember { Animatable(if (reduced) 1f else 0f) }
    LaunchedEffect(Unit) {
        if (!reduced) {
            delay(index * STAGGER_MS)
            entrance.animateTo(1f, spring(dampingRatio = 0.72f, stiffness = 380f))
        }
    }
    // While editing, the tiles wiggle the way home screen icons do, unless motion is reduced.
    val wiggle = if (editing && !reduced) {
        val transition = rememberInfiniteTransition(label = "wiggle")
        transition.animateFloat(
            initialValue = -1.2f,
            targetValue = 1.2f,
            animationSpec = infiniteRepeatable(tween(260 + index % 3 * 40), RepeatMode.Reverse),
            label = "wiggle angle",
        ).value
    } else {
        0f
    }
    val fade by animateFloatAsState(if (faded) 0.45f else 1f, tween(motion.standard), label = "fade")

    val title = stringResource(PlaceLook.title(place))
    val stateText = stringResource(if (pinned) R.string.places_pinned else R.string.places_not_pinned)
    val action = if (editing) {
        Modifier.toggleable(value = pinned, role = Role.Checkbox, onValueChange = { onClick() })
            .semantics { stateDescription = stateText }
    } else {
        Modifier.clickable(role = Role.Button, onClick = onClick)
    }

    Surface(
        shape = AppTheme.shapes.card,
        color = container,
        contentColor = content,
        border = if (hero) null else BorderStroke(1.dp, colors.outline.copy(alpha = 0.28f)),
        modifier = Modifier
            .graphicsLayer {
                val t = entrance.value
                alpha = t * fade
                translationY = (1f - t) * 14.dp.toPx()
                scaleX = 0.96f + 0.04f * t
                scaleY = 0.96f + 0.04f * t
                rotationZ = wiggle
            }
            .clip(AppTheme.shapes.card)
            .then(action),
    ) {
        Box {
            Column(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth().heightIn(min = 118.dp).padding(AppTheme.density.cardPadding.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(PlaceLook.icon(place), contentDescription = null, tint = iconTint, modifier = Modifier.size(22.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(
                        title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    if (pinned && !editing) {
                        Icon(
                            Icons.Filled.PushPin,
                            contentDescription = stateText,
                            tint = if (hero) content.copy(alpha = 0.7f) else colors.textMuted,
                            modifier = Modifier.size(16.dp),
                        )
                    }
                }
                Spacer(Modifier.weight(1f, fill = false))
                Live(place, digest, hero)
            }
            if (editing) PinToggle(pinned, hero, Modifier.align(Alignment.TopEnd).padding(8.dp))
        }
    }
}

/** The pin toggle on a tile while editing: filled in the accent when pinned. */
@Composable
private fun PinToggle(pinned: Boolean, hero: Boolean, modifier: Modifier) {
    val colors = AppTheme.colors
    val fill by animateColorAsState(
        if (pinned) colors.accent else if (hero) Color.Transparent else colors.surfaceVariant,
        tween(AppTheme.motion.standard),
        label = "pin fill",
    )
    val tint = if (pinned) colors.onAccent else if (hero) colors.onHero else colors.textMuted
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(28.dp)
            .background(fill, CircleShape)
            .border(1.dp, if (pinned) colors.accent else tint.copy(alpha = 0.6f), CircleShape),
    ) {
        Icon(Icons.Filled.PushPin, contentDescription = null, tint = tint, modifier = Modifier.size(16.dp))
    }
}

/** The live part of a tile: a ring, a big number or a line, whichever says the most about the place. */
@Composable
private fun Live(place: String, digest: PlacesDigest, hero: Boolean) {
    val muted = if (hero) AppTheme.colors.onHero.copy(alpha = 0.8f) else AppTheme.colors.textMuted
    when (place) {
        PlaceRules.TODAY -> Ringed(digest.todayDone, digest.todayTotal)
        PlaceRules.HABITS ->
            if (digest.habitsDue == 0) Line(stringResource(R.string.places_habits_none), muted)
            else Ringed(digest.habitsMet, digest.habitsDue)
        PlaceRules.GOALS ->
            if (digest.goalsTotal == 0) Line(stringResource(R.string.places_goals_none), muted)
            else Ringed(digest.goalsHit, digest.goalsTotal)
        PlaceRules.INBOX -> BigNumber(digest.inbox, stringResource(R.string.places_inbox), muted)
        PlaceRules.TOMORROW -> Line(stringResource(R.string.places_tomorrow, digest.tomorrow), muted)
        PlaceRules.CALENDAR -> Line(stringResource(R.string.places_calendar, digest.comingWeek), muted)
        PlaceRules.PROJECTS -> Line(stringResource(R.string.places_projects, digest.projectsOpen, digest.projectsDoing), muted)
        PlaceRules.REVIEWS ->
            if (digest.letterWaiting) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.Email, contentDescription = null, tint = AppTheme.colors.heroAccent, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.places_letter), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                }
            } else {
                Line(stringResource(R.string.places_reviews), muted)
            }
        PlaceRules.STATS -> Line(stringResource(R.string.places_stats, digest.doneThisWeek), muted)
        PlaceRules.ARCHIVE -> Line(stringResource(R.string.places_archive, digest.archived), muted)
        PlaceRules.WANTS ->
            if (digest.wantsReady > 0) BigNumber(digest.wantsReady, stringResource(R.string.wants_status_ready), muted)
            else Line(stringResource(R.string.places_wants_cooling, digest.wantsCooling), muted)
        PlaceRules.LIFE_GOALS ->
            if (digest.lifeGoalsOpen == 0) Line(stringResource(R.string.places_life_goals_none), muted)
            else Line(pluralStringResource(R.plurals.places_life_goals, digest.lifeGoalsOpen, digest.lifeGoalsOpen), muted)
        PlaceRules.TALLY ->
            if (digest.tallyToday.isEmpty()) {
                Line(stringResource(R.string.places_tally_none), muted)
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    TallyStackedBar(digest.tallyToday, height = 12.dp)
                    Line(stringResource(R.string.places_tally, durationText(digest.tallyToday.sumOf(TallySlice::minutes))), muted)
                }
            }
    }
}

@Composable
private fun Ringed(done: Int, total: Int) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        ProgressRing(fraction = if (total == 0) 0f else done.toFloat() / total, size = 40.dp, stroke = 6.dp)
        Spacer(Modifier.width(10.dp))
        Text(done.toString(), style = AppTheme.type.number.copy(fontSize = 24.sp), color = AppTheme.colors.text)
        Spacer(Modifier.width(4.dp))
        Text(stringResource(R.string.places_out_of, total), style = MaterialTheme.typography.bodyMedium, color = AppTheme.colors.textMuted)
    }
}

@Composable
private fun BigNumber(value: Int, label: String, muted: Color) {
    Row(verticalAlignment = Alignment.Bottom) {
        Text(value.toString(), style = AppTheme.type.number.copy(fontSize = 28.sp), color = AppTheme.colors.text)
        Spacer(Modifier.width(6.dp))
        Text(label, style = MaterialTheme.typography.bodyMedium, color = muted, modifier = Modifier.padding(bottom = 4.dp))
    }
}

@Composable
private fun Line(text: String, color: Color) {
    Text(text, style = MaterialTheme.typography.bodyMedium, color = color, maxLines = 2, overflow = TextOverflow.Ellipsis)
}

private const val STAGGER_MS = 30L
