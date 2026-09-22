package com.goalmaker.app.ui.lock

import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.goalmaker.app.R
import com.goalmaker.app.application.auth.UnlockAvailability
import com.goalmaker.app.data.auth.DeviceUnlock
import com.goalmaker.app.ui.components.GoalMakerLogo

/**
 * The lock over the signed-in app (docs/sign-in.md). It asks the phone once as it appears, and
 * offers to ask again after that. A phone that cannot ask at all is not a dead end: the emailed
 * code is always the way through, so the owner is never shut out of their own planner.
 */
@Composable
fun LockScreen(unlock: DeviceUnlock, onUnlocked: () -> Unit, onUseCode: () -> Unit) {
    val activity = LocalActivity.current as? FragmentActivity
    val availability = remember { if (activity == null) UnlockAvailability.UNAVAILABLE else unlock.availability() }
    var problem by remember { mutableStateOf<String?>(null) }
    var asking by remember { mutableStateOf(false) }
    val canAsk = activity != null && availability == UnlockAvailability.READY

    val title = stringResource(R.string.lock_title)
    val subtitle = stringResource(R.string.lock_subtitle)
    val ask = {
        if (canAsk && !asking) {
            problem = null
            asking = true
            unlock.ask(
                activity = requireNotNull(activity),
                title = title,
                subtitle = subtitle,
                onUnlocked = {
                    asking = false
                    onUnlocked()
                },
                onGaveUp = { message ->
                    asking = false
                    problem = message
                },
            )
        }
    }
    // Asks as the lock appears, and again each time the app comes back to the screen, because
    // leaving takes the prompt down with it and the owner should not have to ask for it twice.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { ask() }

    // Back does not go round the lock; it leaves the app the way Home does.
    BackHandler(enabled = true) { activity?.moveTaskToBack(true) }

    Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier.fillMaxSize().safeDrawingPadding().padding(24.dp),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                modifier = Modifier.widthIn(max = 480.dp).fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                GoalMakerLogo(size = 56.dp)
                Text(
                    text = stringResource(R.string.lock_title),
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onBackground,
                )
                Text(
                    text = problem ?: stringResource(reasonFor(availability)),
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (problem == null) {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    } else {
                        MaterialTheme.colorScheme.error
                    },
                )
                if (canAsk) {
                    Button(onClick = ask) { Text(stringResource(R.string.lock_unlock)) }
                }
                TextButton(onClick = onUseCode) { Text(stringResource(R.string.lock_use_code)) }
            }
        }
    }
}

private fun reasonFor(availability: UnlockAvailability) = when (availability) {
    UnlockAvailability.READY -> R.string.lock_subtitle
    UnlockAvailability.NOTHING_ENROLLED -> R.string.lock_nothing_enrolled
    UnlockAvailability.UNAVAILABLE -> R.string.lock_unavailable
}
