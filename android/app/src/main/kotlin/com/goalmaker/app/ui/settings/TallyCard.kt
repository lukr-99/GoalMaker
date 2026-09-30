package com.goalmaker.app.ui.settings

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.goalmaker.app.R

/**
 * Tally (docs/tally.md, ADR 0013): the switch, and while usage access is missing, a card that says
 * what is read and what syncs and opens the system's Usage access page.
 */
@Composable
fun TallyCard(viewModel: TallyViewModel) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    // Coming back from the system's page resumes this window, and access can be taken away there too.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.checkAccess() }
    SwitchRow(
        title = stringResource(R.string.settings_tally_switch),
        hint = stringResource(R.string.settings_tally_hint),
        checked = state.on,
        onCheckedChange = viewModel::setOn,
    )
    if (state.askForAccess) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.tally_access_title), style = MaterialTheme.typography.titleSmall)
                Text(stringResource(R.string.tally_access_read), style = MaterialTheme.typography.bodyMedium)
                Text(stringResource(R.string.tally_access_syncs), style = MaterialTheme.typography.bodyMedium)
                Text(stringResource(R.string.tally_access_how), style = MaterialTheme.typography.bodySmall)
                Button(onClick = { openUsageAccess(context) }) { Text(stringResource(R.string.tally_access_open)) }
            }
        }
    }
}

// GoalMaker's own row on the Usage access page where the phone has one, otherwise the whole list.
private fun openUsageAccess(context: Context) {
    val page = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)
    try {
        context.startActivity(Intent(page).setData(Uri.fromParts("package", context.packageName, null)))
    } catch (_: ActivityNotFoundException) {
        try {
            context.startActivity(page)
        } catch (_: ActivityNotFoundException) {
            context.startActivity(Intent(Settings.ACTION_SETTINGS))
        }
    }
}
