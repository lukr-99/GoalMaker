package com.goalmaker.app.ui.goals

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Event
import androidx.compose.material.icons.outlined.TrackChanges
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import com.goalmaker.app.R
import com.goalmaker.app.application.planning.GoalDraft
import com.goalmaker.app.application.planning.GoalHorizon
import com.goalmaker.app.application.planning.GoalItem
import com.goalmaker.app.application.planning.GoalRules
import com.goalmaker.app.domain.composer.SpanKind
import com.goalmaker.app.ui.composer.ComposerChip
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * The bottom bar's preview on Goals (docs/composer.md): the period, named the way the goals page names
 * it ("This week", "Today", "Next week", "October", "2027"), and the target ("Target 30 km") or "Done
 * or not". Nothing while the line is empty.
 */
@Composable
internal fun goalLineChips(line: String, draft: GoalDraft, today: LocalDate): List<ComposerChip> {
    if (line.isBlank()) return emptyList()
    val locale = LocalConfiguration.current.locales[0]
    val horizon = draft.horizon
    val current = GoalRules.periodStart(horizon, today)
    val next = GoalRules.periodStart(horizon, GoalRules.periodEnd(horizon, current).plusDays(1))
    val start = draft.periodStart
    val period = when {
        start == current -> stringResource(horizon.thisLabel())
        start == next -> stringResource(horizon.nextLabel())
        horizon == GoalHorizon.YEAR -> start.year.toString()
        horizon == GoalHorizon.MONTH -> DateTimeFormatter.ofPattern(if (start.year == today.year) "LLLL" else "LLLL yyyy", locale).format(start)
        else -> DateTimeFormatter.ofPattern("d MMM", locale).format(start)
    }
    val target = draft.target
    val unit = draft.unit
    val measure = when {
        draft.mode != GoalRules.MODE_NUMBER || target == null -> stringResource(R.string.goals_mode_done)
        unit == null -> stringResource(R.string.bar_goal_target, amountText(target, locale))
        else -> stringResource(R.string.bar_goal_target_unit, amountText(target, locale), unit)
    }
    return listOf(
        chip(period, Icons.Outlined.Event),
        chip(measure, if (draft.mode == GoalRules.MODE_NUMBER) Icons.Outlined.TrackChanges else Icons.Outlined.CheckCircle),
    )
}

/** A draft as a goal not saved yet (an empty id), for the goal form. */
internal fun GoalDraft.asNewGoal(): GoalItem = GoalItem(
    id = "",
    title = title,
    horizon = horizon,
    periodStart = periodStart,
    mode = mode,
    emoji = emoji,
    parentId = parentId,
    target = target,
    unit = unit,
)

private fun chip(label: String, icon: ImageVector) = ComposerChip(SpanKind.DATE, label, null, false, null, emptyList(), icon = icon)
