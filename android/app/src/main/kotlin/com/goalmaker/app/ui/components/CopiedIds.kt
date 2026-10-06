package com.goalmaker.app.ui.components

import android.os.Build
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.text.AnnotatedString
import com.goalmaker.app.R
import kotlinx.coroutines.flow.Flow

/**
 * Puts each item id [copies] sends (GM-12) on the clipboard, with the usual confirmation: Android 13
 * and later show their own, so the app says "Copied GM-12" in [snackbars] only on older phones.
 */
@Suppress("DEPRECATION")
@Composable
fun CopiedIds(copies: Flow<String>, snackbars: SnackbarHostState) {
    val clipboard = LocalClipboardManager.current
    val resources = LocalResources.current
    LaunchedEffect(copies) {
        copies.collect { id ->
            clipboard.setText(AnnotatedString(id))
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
                snackbars.showSnackbar(resources.getString(R.string.projects_id_copied, id), duration = SnackbarDuration.Short)
            }
        }
    }
}
