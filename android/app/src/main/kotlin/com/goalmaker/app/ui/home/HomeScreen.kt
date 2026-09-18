package com.goalmaker.app.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.goalmaker.app.R

/** Today, as far as M0 goes: who is signed in, and where the composer will live. */
@Composable
fun HomeScreen(email: String, onOpenSettings: () -> Unit) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeFlexibleTopAppBar(
                title = { Text(stringResource(R.string.home_title)) },
                subtitle = { Text(stringResource(R.string.home_greeting, email)) },
                actions = {
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Outlined.Settings, contentDescription = stringResource(R.string.home_settings))
                    }
                },
                scrollBehavior = scrollBehavior,
            )
        },
        bottomBar = { ComposerPlaceholder() },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                ),
                shape = RoundedCornerShape(28.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.home_spine_title), style = MaterialTheme.typography.titleLarge)
                    Text(stringResource(R.string.home_spine_body), style = MaterialTheme.typography.bodyLarge)
                }
            }
        }
    }
}

@Composable
private fun ComposerPlaceholder() {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = RoundedCornerShape(28.dp),
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .imePadding()
            .padding(12.dp),
    ) {
        Text(
            text = stringResource(R.string.home_composer_placeholder),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
        )
    }
}
