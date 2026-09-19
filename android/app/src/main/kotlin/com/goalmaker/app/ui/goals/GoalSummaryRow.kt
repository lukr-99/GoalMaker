package com.goalmaker.app.ui.goals

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.goalmaker.app.ui.components.ProgressRing
import com.goalmaker.app.ui.theme.AppTheme

/** A goal on one line with its ring and where it stands, for Today's folded section of this week's goals. */
@Composable
fun GoalSummaryRow(row: GoalRow, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val locale = LocalConfiguration.current.locales[0]
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = AppTheme.density.rowMinHeight.dp)
            .background(AppTheme.colors.surface, AppTheme.shapes.row)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp),
    ) {
        ProgressRing(row.progress.fraction.toFloat(), size = 28.dp, stroke = 3.dp) {
            row.goal.emoji?.let { Text(it, style = MaterialTheme.typography.labelSmall) }
        }
        Text(
            row.goal.title,
            style = MaterialTheme.typography.bodyLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f).padding(horizontal = 12.dp),
        )
        Text(progressText(row, locale), style = MaterialTheme.typography.bodySmall, color = AppTheme.colors.textMuted)
    }
}
