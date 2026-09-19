package com.goalmaker.app.application.connector

import java.time.Instant

/** A connector link as the owner may see it: when it was made, last used and revoked, never its secret. */
data class ConnectorLink(
    val id: String,
    val createdAt: Instant,
    val lastUsedAt: Instant?,
    val revokedAt: Instant?,
) {
    val active: Boolean get() = revokedAt == null
}
