package com.goalmaker.app.application.environment

/** Which Supabase project the app talks to: its API URL and publishable key. */
data class BackendEnvironment(
    val url: String,
    val publishableKey: String,
) {
    val isConfigured: Boolean get() = url.isNotBlank() && publishableKey.isNotBlank()
}
