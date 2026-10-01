package com.goalmaker.app.ui.habits

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.goalmaker.app.R
import com.goalmaker.app.ui.theme.AppTheme

/**
 * The short card Today shows once every habit on it is done (contracts/vectors/habits.json, allDone),
 * on the theme's hero colors. It pops in with a spring unless motion is reduced, and readers hear it.
 */
@Composable
fun AllDoneCard(modifier: Modifier = Modifier) {
    val colors = AppTheme.colors
    val reduceMotion = AppTheme.reduceMotion
    // It arrives the moment the last habit is done, so it starts hidden and comes in.
    val shown = remember { MutableTransitionState(false).apply { targetState = true } }
    AnimatedVisibility(
        visibleState = shown,
        enter = if (reduceMotion) fadeIn(tween(AppTheme.motion.quick)) else fadeIn() + scaleIn(initialScale = 0.9f),
        modifier = modifier,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .background(colors.hero, AppTheme.shapes.card)
                .padding(16.dp)
                .semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite },
        ) {
            Icon(Icons.Filled.Verified, contentDescription = null, tint = colors.heroAccent, modifier = Modifier.size(28.dp))
            Text(
                stringResource(R.string.habits_all_done_card),
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Bold,
                color = colors.onHero,
            )
        }
    }
}
