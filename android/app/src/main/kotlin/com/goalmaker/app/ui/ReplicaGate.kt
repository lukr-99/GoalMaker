package com.goalmaker.app.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.LoadingIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.goalmaker.app.application.sync.ReplicaOpening
import com.goalmaker.app.composition.AppGraph

/**
 * Shows [content] once the replica is open, and the startup failure screen when it would not open,
 * so no screen ever reads a replica that is not there (M6-06).
 */
@Composable
fun ReplicaGate(graph: AppGraph, content: @Composable () -> Unit) {
    val opening by graph.replicaOpening.collectAsStateWithLifecycle()
    when (val current = opening) {
        ReplicaOpening.Opening -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            LoadingIndicator(Modifier.size(64.dp))
        }
        is ReplicaOpening.Failed -> StartupFailureScreen(newerApp = current.newerApp)
        ReplicaOpening.Open -> content()
    }
}
