package com.goalmaker.app.ui.components

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarDefaults
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * The app's snackbars. Their action, Undo above all, sits on a neutral tint in the snackbar's own text
 * colour: the theme's accent on a light snackbar, lime above all, was too faint to find (the owner asked).
 */
@Composable
fun AppSnackbarHost(state: SnackbarHostState) {
    SnackbarHost(state) { data ->
        val text = SnackbarDefaults.contentColor
        // The same margin the stock snackbar keeps from the screen's edges.
        Snackbar(
            modifier = Modifier.padding(12.dp),
            action = data.visuals.actionLabel?.let { label ->
                {
                    TextButton(
                        onClick = data::performAction,
                        colors = ButtonDefaults.textButtonColors(containerColor = text.copy(alpha = ACTION_TINT), contentColor = text),
                    ) { Text(label) }
                }
            },
        ) { Text(data.visuals.message) }
    }
}

// How strongly the action's background shows through: enough to read as a button, not to shout.
private const val ACTION_TINT = 0.12f
