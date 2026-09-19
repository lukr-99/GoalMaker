package com.goalmaker.app.ui.connector

import com.goalmaker.app.application.connector.ConnectorLink

/**
 * The connector screen: the active link (never its secret), or none; [newUrl] is a link just made,
 * shown once; [unavailable] when the server can't be reached; [busy] while a call runs.
 */
data class ConnectorUiState(
    val loaded: Boolean = false,
    val active: ConnectorLink? = null,
    val newUrl: String? = null,
    val unavailable: Boolean = false,
    val busy: Boolean = false,
)
