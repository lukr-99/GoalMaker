package com.goalmaker.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
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
 * where to look, rather than the phone closing an app that never appeared. It uses no theme of its
 * own, because the thing that loads the theme is what failed.
 */
@Composable
fun StartupFailureScreen() {
    MaterialTheme {
        Surface(Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier.fillMaxSize().padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
            ) {
                Text(stringResource(R.string.startup_failed_title), style = MaterialTheme.typography.headlineSmall)
                Text(stringResource(R.string.startup_failed), style = MaterialTheme.typography.bodyLarge)
            }
        }
    }
}
