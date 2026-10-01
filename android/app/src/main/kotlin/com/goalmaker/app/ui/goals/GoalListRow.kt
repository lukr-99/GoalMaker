package com.goalmaker.app.ui.goals

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.goalmaker.app.R
import com.goalmaker.app.application.planning.GoalRules
import com.goalmaker.app.ui.components.GoalMakerCheckbox
import com.goalmaker.app.ui.theme.AppTheme

/**
 * A goal on one compact row of the Goals list view (docs/goals.md): its box when it is done or not, its
 * name with where it stands and a thin bar, its pace and the quick log. A tap opens its editor.
 */
@Composable
internal fun GoalListRow(row: GoalRow, onOpen: () -> Unit, onQuickLog: () -> Unit, onStatus: (String) -> Unit, modifier: Modifier = Modifier) {
    val goal = row.goal
    val locale = LocalConfiguration.current.locales[0]
    val done = goal.status == GoalRules.DONE
    val counts = goal.mode == GoalRules.MODE_NUMBER || (goal.mode == GoalRules.MODE_TASKS && row.progress.target > 0.0)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = AppTheme.density.rowMinHeight.dp)
            .background(AppTheme.colors.surface, AppTheme.shapes.row)
            .clickable(onClickLabel = stringResource(R.string.goals_open_action), onClick = onOpen)
            .padding(start = if (goal.mode == GoalRules.MODE_DONE) 0.dp else 12.dp, end = 8.dp, top = 6.dp, bottom = 6.dp),
    ) {
        if (goal.mode == GoalRules.MODE_DONE) {
            val label = stringResource(R.string.goals_done_box, goal.title)
            GoalMakerCheckbox(
                checked = done,
                onCheckedChange = { checked -> onStatus(if (checked) GoalRules.DONE else GoalRules.OPEN) },
                modifier = Modifier.semantics { contentDescription = label },
            )
        }
        Column(verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    listOfNotNull(goal.emoji, goal.title).joinToString(" "),
                    style = MaterialTheme.typography.bodyLarge,
                    textDecoration = if (done) TextDecoration.LineThrough else null,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    progressText(row, locale),
                    style = MaterialTheme.typography.bodySmall,
                    color = AppTheme.colors.textMuted,
                    maxLines = 1,
                    modifier = Modifier.padding(start = 8.dp),
                )
            }
            if (counts) ThinBar(row.progress.fraction.toFloat())
        }
        PaceChip(row, locale)
        QuickLog(row, locale, onQuickLog)
    }
}

// The list's bar: a hairline of the accent over a faint track.
@Composable
private fun ThinBar(fraction: Float) {
    Box(
        Modifier
            .fillMaxWidth()
            .height(4.dp)
            .background(AppTheme.colors.outline.copy(alpha = 0.3f), RoundedCornerShape(50)),
    ) {
        Box(
            Modifier
                .fillMaxWidth(fraction.coerceIn(0f, 1f))
                .height(4.dp)
                .background(AppTheme.colors.accent, RoundedCornerShape(50)),
        )
    }
}
