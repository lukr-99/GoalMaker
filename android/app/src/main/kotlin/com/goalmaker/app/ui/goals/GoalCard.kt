package com.goalmaker.app.ui.goals

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.CallMade
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.goalmaker.app.R
import com.goalmaker.app.application.planning.GoalItem
import com.goalmaker.app.application.planning.GoalPace
import com.goalmaker.app.application.planning.GoalRules
import com.goalmaker.app.ui.components.GoalMakerCheckbox
import com.goalmaker.app.ui.components.ProgressRing
import com.goalmaker.app.ui.theme.AppTheme
import java.util.Locale

/**
 * A goal on the ladder (design prototype v2, option A's card): its ring or box, name and the goal it
 * feeds, the quick log, the big number with its bar, and its pace. A tap lights its chain; with a chain
 * lit, [lit] says whether this card is in it (null: no chain lit) and the others fade.
 */
@Composable
internal fun GoalCard(
    row: GoalRow,
    lit: Boolean?,
    picked: Boolean,
    onPick: () -> Unit,
    onQuickLog: () -> Unit,
    onLog: () -> Unit,
    onEdit: () -> Unit,
    onStatus: (String) -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val goal = row.goal
    val locale = LocalConfiguration.current.locales[0]
    val done = goal.status == GoalRules.DONE
    val shape = AppTheme.shapes.card
    val inChain = stringResource(R.string.goals_in_chain)
    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = modifier
            .fillMaxWidth()
            .alpha(if (lit == false) DIMMED else 1f)
            .let { if (picked || lit == true) it.border(if (picked) 3.dp else 2.dp, AppTheme.colors.accent, shape) else it }
            .background(AppTheme.colors.surface, shape)
            .clickable(onClickLabel = stringResource(if (picked) R.string.goals_unpick_action else R.string.goals_pick_action), onClick = onPick)
            .semantics {
                selected = picked
                if (lit == true) stateDescription = inChain
            }
            .padding(start = 14.dp, end = 4.dp, top = 10.dp, bottom = 12.dp),
    ) {
        Row(verticalAlignment = Alignment.Top) {
            if (goal.mode == GoalRules.MODE_DONE) {
                val label = stringResource(R.string.goals_done_box, goal.title)
                GoalMakerCheckbox(
                    checked = done,
                    onCheckedChange = { checked -> onStatus(if (checked) GoalRules.DONE else GoalRules.OPEN) },
                    modifier = Modifier.semantics { contentDescription = label },
                )
            } else {
                ProgressRing(row.progress.fraction.toFloat(), size = 26.dp, stroke = 4.dp, modifier = Modifier.padding(top = 10.dp))
            }
            Column(Modifier.weight(1f).padding(start = 10.dp, top = 8.dp)) {
                Text(
                    listOfNotNull(goal.emoji, goal.title).joinToString(" "),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    textDecoration = if (done) TextDecoration.LineThrough else null,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                row.parentTitle?.let { parent ->
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 2.dp)) {
                        Icon(Icons.AutoMirrored.Outlined.CallMade, contentDescription = null, tint = AppTheme.colors.textMuted, modifier = Modifier.size(14.dp))
                        Text(
                            stringResource(R.string.goals_feeds, parent),
                            style = MaterialTheme.typography.bodySmall,
                            color = AppTheme.colors.textMuted,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(start = 4.dp),
                        )
                    }
                }
            }
            QuickLog(row, locale, onQuickLog, Modifier.padding(top = 4.dp))
            GoalMenu(goal, onLog, onEdit, onStatus, onDelete)
        }
        Column(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(end = 10.dp)) {
            Amount(row, locale)
            PaceChip(row, locale)
        }
    }
}

// The big number and its bar for a goal that counts; a line of words for one that is done or not.
@Composable
private fun Amount(row: GoalRow, locale: Locale) {
    val goal = row.goal
    val counts = goal.mode == GoalRules.MODE_NUMBER || (goal.mode == GoalRules.MODE_TASKS && row.progress.target > 0.0)
    if (!counts) {
        Text(progressText(row, locale), style = MaterialTheme.typography.bodyMedium, color = AppTheme.colors.textMuted)
        return
    }
    val of = when (goal.mode) {
        GoalRules.MODE_TASKS -> row.progress.target.toInt().let { pluralStringResource(R.plurals.goals_of_tasks, it, it) }
        else -> {
            val target = amountText(row.progress.target, locale)
            goal.unit?.let { stringResource(R.string.goals_of_unit, target, it) } ?: stringResource(R.string.goals_of, target)
        }
    }
    val words = progressText(row, locale)
    Column(
        verticalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier.clearAndSetSemantics { contentDescription = words },
    ) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(amountText(row.progress.value, locale), style = AppTheme.type.number.copy(fontSize = 26.sp, lineHeight = 28.sp), color = AppTheme.colors.accent)
            Text(of, style = MaterialTheme.typography.bodySmall, color = AppTheme.colors.textMuted, modifier = Modifier.padding(start = 6.dp, bottom = 3.dp))
        }
        Box(
            Modifier
                .fillMaxWidth()
                .height(8.dp)
                .background(AppTheme.colors.outline.copy(alpha = 0.3f), RoundedCornerShape(50)),
        ) {
            Box(
                Modifier
                    .fillMaxWidth(row.progress.fraction.toFloat().coerceIn(0f, 1f))
                    .height(8.dp)
                    .background(AppTheme.colors.accent, RoundedCornerShape(50)),
            )
        }
    }
}

// On track, Hit, Needs you or Behind by 6 km, in a small pill.
@Composable
internal fun PaceChip(row: GoalRow, locale: Locale) {
    val standing = row.standing
    val colors = AppTheme.colors
    val (fill, ink) = when (standing.pace) {
        GoalPace.HIT -> colors.accent to colors.onAccent
        GoalPace.BEHIND -> colors.danger.copy(alpha = 0.2f) to colors.text
        GoalPace.DROPPED -> colors.surfaceVariant to colors.textMuted
        GoalPace.ON_TRACK -> colors.surfaceVariant to colors.text
    }
    Text(
        paceText(row, locale),
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.Bold,
        color = ink,
        modifier = Modifier.background(fill, RoundedCornerShape(50)).padding(horizontal = 8.dp, vertical = 2.dp),
    )
}

// "+5 km" repeats the latest amount; "Log" asks for one when nothing was logged yet.
@Composable
internal fun QuickLog(row: GoalRow, locale: Locale, onQuickLog: () -> Unit, modifier: Modifier = Modifier) {
    val goal = row.goal
    if (goal.mode != GoalRules.MODE_NUMBER || goal.status != GoalRules.OPEN || row.progress.hit) return
    val text = row.quickAmount?.let { amount ->
        val value = amountText(amount, locale)
        goal.unit?.let { stringResource(R.string.goals_quick_unit, value, it) } ?: stringResource(R.string.goals_quick, value)
    }
    val description = if (text == null) stringResource(R.string.goals_log_on, goal.title) else stringResource(R.string.goals_quick_description, text, goal.title)
    FilledTonalButton(
        onClick = onQuickLog,
        contentPadding = PaddingValues(horizontal = 12.dp),
        colors = ButtonDefaults.filledTonalButtonColors(containerColor = AppTheme.colors.surfaceVariant, contentColor = AppTheme.colors.text),
        modifier = modifier.semantics { contentDescription = description },
    ) {
        if (text == null) {
            Icon(Icons.Outlined.Add, contentDescription = null, modifier = Modifier.size(18.dp))
            Text(stringResource(R.string.goals_log_short), modifier = Modifier.padding(start = 4.dp))
        } else {
            Text(text, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun GoalMenu(goal: GoalItem, onLog: () -> Unit, onEdit: () -> Unit, onStatus: (String) -> Unit, onDelete: () -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }) {
            Icon(Icons.Outlined.MoreVert, contentDescription = stringResource(R.string.goals_more, goal.title))
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            @Composable
            fun Choice(label: String, danger: Boolean = false, action: () -> Unit) = DropdownMenuItem(
                text = { Text(label, color = if (danger) AppTheme.colors.danger else MaterialTheme.colorScheme.onSurface) },
                onClick = {
                    open = false
                    action()
                },
            )
            Choice(stringResource(R.string.goals_edit_short), action = onEdit)
            if (goal.status == GoalRules.OPEN) {
                if (goal.mode == GoalRules.MODE_NUMBER) Choice(stringResource(R.string.goals_log), action = onLog)
                Choice(stringResource(R.string.goals_mark_done)) { onStatus(GoalRules.DONE) }
                Choice(stringResource(R.string.goals_drop)) { onStatus(GoalRules.DROPPED) }
            } else {
                Choice(stringResource(R.string.goals_reopen)) { onStatus(GoalRules.OPEN) }
            }
            Choice(stringResource(R.string.goals_delete), danger = true, action = onDelete)
        }
    }
}

/** A goal's pace in words: "On track", "Hit", "Needs you", "Behind by 6 km". */
@Composable
internal fun paceText(row: GoalRow, locale: Locale): String {
    val standing = row.standing
    return when (standing.pace) {
        GoalPace.HIT -> stringResource(R.string.goals_pace_hit)
        GoalPace.ON_TRACK -> stringResource(R.string.goals_pace_on_track)
        GoalPace.DROPPED -> stringResource(R.string.goals_pace_dropped)
        GoalPace.BEHIND -> {
            val behind = standing.behind ?: return stringResource(R.string.goals_pace_needs_you)
            when {
                row.goal.mode == GoalRules.MODE_TASKS -> behind.toInt().let { pluralStringResource(R.plurals.goals_pace_behind_tasks, it, it) }
                row.goal.unit != null -> stringResource(R.string.goals_pace_behind_unit, amountText(behind, locale), row.goal.unit)
                else -> stringResource(R.string.goals_pace_behind, amountText(behind, locale))
            }
        }
    }
}

private const val DIMMED = 0.32f
