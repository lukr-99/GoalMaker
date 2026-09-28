package com.goalmaker.app.ui.nav

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.goalmaker.app.R

/**
 * The top bar's leading icon on a place: a back arrow when the place was opened from somewhere (the
 * Places hub, or a link on Today), otherwise [pinned], the mark a place in the bottom bar wears.
 */
@Composable
fun PlaceNavigationIcon(onBack: (() -> Unit)?, pinned: @Composable () -> Unit = { AppMark() }) {
    if (onBack == null) {
        pinned()
    } else {
        IconButton(onClick = onBack) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.settings_back))
        }
    }
}
