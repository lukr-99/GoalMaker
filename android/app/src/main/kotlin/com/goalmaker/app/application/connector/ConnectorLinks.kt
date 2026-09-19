package com.goalmaker.app.application.connector

/**
 * The owner's Claude connector links (docs/connector.md, supabase/migrations/0007). Every call throws
 * RemoteUnavailableException when the server can't be reached.
 */
interface ConnectorLinks {
    /** Every link the owner made, newest first. */
    suspend fun list(): List<ConnectorLink>

    /** Makes a new link, which kills the previous one, and returns its secret. The server keeps only a hash. */
    suspend fun create(): String

    /** Kills every active link. */
    suspend fun revoke()

    companion object {
        /** The URL to paste into Claude: the backend's connector function with the secret. */
        fun url(backendUrl: String, secret: String): String = backendUrl.trimEnd('/') + "/functions/v1/connector/" + secret
    }
}
