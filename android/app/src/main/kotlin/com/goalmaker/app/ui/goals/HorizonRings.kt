package com.goalmaker.app.ui.goals

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.goalmaker.app.R
import com.goalmaker.app.application.planning.GoalHorizon
import com.goalmaker.app.ui.components.ProgressRing
import com.goalmaker.app.ui.theme.AppTheme
import java.text.NumberFormat

/**
 * The dashboard over the ladder (design prototype v1, option C): one ring per horizon with how much of
 * its goals is done and how many are hit. A tap shows only that horizon's rung; another tap shows all.
 */
@Composable
internal fun HorizonRings(rings: List<GoalSection>, filter: GoalHorizon?, onFilter: (GoalHorizon) -> Unit, modifier: Modifier = Modifier) {
    val locale = LocalConfiguration.current.locales[0]
    val percent = NumberFormat.getPercentInstance(locale)
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = modifier.fillMaxWidth()) {
        rings.forEach { ring ->
            val shown = filter == ring.horizon
            val name = stringResource(ring.horizon.ringLabel())
            val hit = stringResource(R.string.goals_ring_hit, ring.hits, ring.rows.size)
            val description = stringResource(R.string.goals_ring_description, name, percent.format(ring.fraction), hit)
            val state = stringResource(if (shown) R.string.goals_ring_shown else R.string.goals_ring_all)
            val shape = AppTheme.shapes.card
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier
                    .weight(1f)
                    .let { if (shown) it.background(AppTheme.colors.surface, shape).border(2.dp, AppTheme.colors.accent, shape) else it }
                    .clickable(role = Role.Button) { onFilter(ring.horizon) }
                    .clearAndSetSemantics {
                        contentDescription = description
                        stateDescription = state
                        selected = shown
                        role = Role.Button
                        onClick { onFilter(ring.horizon); true }
                    }
                    .padding(top = 10.dp, bottom = 8.dp, start = 2.dp, end = 2.dp),
            ) {
                ProgressRing(ring.fraction.toFloat(), size = 64.dp, stroke = 7.dp) {
                    Text(
                        percent.format(ring.fraction),
                        style = AppTheme.type.number.copy(fontSize = 15.sp),
                        color = AppTheme.colors.text,
                    )
                }
                Text(name, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                Text(hit, style = MaterialTheme.typography.labelSmall, color = AppTheme.colors.textMuted, textAlign = TextAlign.Center)
            }
        }
    }
}

private fun GoalHorizon.ringLabel() = when (this) {
    GoalHorizon.YEAR -> R.string.goals_ring_year
    GoalHorizon.MONTH -> R.string.goals_ring_month
    GoalHorizon.WEEK -> R.string.goals_ring_week
    GoalHorizon.DAY -> R.string.goals_ring_day
}
