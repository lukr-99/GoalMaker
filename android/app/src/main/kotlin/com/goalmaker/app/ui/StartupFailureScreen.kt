package com.goalmaker.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.goalmaker.app.R

/**
 * GoalMaker could not start, usually because its data file will not open (M6-06). It says so and
 * what to do next, rather than the phone closing an app that never appeared. [newerApp] when a newer
 * GoalMaker wrote the data, which updating fixes. The caller gives it a theme: the app's own when
 * the replica is what failed, a plain one when the thing that loads the theme did.
 */
@Composable
fun StartupFailureScreen(newerApp: Boolean = false) {
    Surface(Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.fillMaxSize().safeDrawingPadding().padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
        ) {
            Text(stringResource(R.string.startup_failed_title), style = MaterialTheme.typography.headlineSmall)
            Text(
                stringResource(if (newerApp) R.string.startup_failed_newer else R.string.startup_failed),
                style = MaterialTheme.typography.bodyLarge,
            )
        }
    }
}
