package com.goalmaker.app.ui.lists

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.goalmaker.app.R
import com.goalmaker.app.application.planning.NewYearNudge
import com.goalmaker.app.ui.theme.AppTheme

/**
 * The January nudge on Today (spec story 66, docs/reviews.md): look back on the year gone and set this
 * year's goals, each offered while it is still to do; Not now puts the card away until next January.
 */
@Composable
internal fun NewYearCard(
    nudge: NewYearNudge,
    onReview: () -> Unit,
    onGoals: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = AppTheme.colors
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(colors.hero, AppTheme.shapes.card)
            .padding(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Icon(Icons.Outlined.AutoAwesome, contentDescription = null, tint = colors.heroAccent, modifier = Modifier.size(24.dp))
            Text(
                stringResource(R.string.new_year_title, nudge.year),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = colors.onHero,
                modifier = Modifier.semantics { heading() },
            )
        }
        Text(
            stringResource(
                when {
                    nudge.review && nudge.goals -> R.string.new_year_both
                    nudge.review -> R.string.new_year_review_only
                    else -> R.string.new_year_goals_only
                },
                nudge.year - 1,
                nudge.year,
            ),
            style = MaterialTheme.typography.bodyMedium,
            color = colors.onHero,
            modifier = Modifier.padding(top = 6.dp, bottom = 10.dp),
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            val buttons = ButtonDefaults.buttonColors(containerColor = colors.heroAccent, contentColor = colors.hero)
            if (nudge.review) Button(onClick = onReview, colors = buttons) { Text(stringResource(R.string.new_year_review, nudge.year - 1)) }
            if (nudge.goals) Button(onClick = onGoals, colors = buttons) { Text(stringResource(R.string.new_year_goals, nudge.year)) }
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.new_year_not_now), color = colors.onHero) }
        }
    }
}
