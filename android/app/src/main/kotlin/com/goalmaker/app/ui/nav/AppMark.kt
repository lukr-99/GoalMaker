package com.goalmaker.app.ui.nav

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.goalmaker.app.R
import com.goalmaker.app.ui.components.GoalMakerLogo

/** The GoalMaker logo at the start of the top bar, where Projects and the Calendar wear it as Tomorrow and the Inbox do. */
@Composable
fun AppMark() {
    val label = stringResource(R.string.app_name)
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .padding(start = 12.dp)
            .size(40.dp)
            .semantics { contentDescription = label },
    ) {
        GoalMakerLogo(size = 32.dp, intro = true)
    }
}
