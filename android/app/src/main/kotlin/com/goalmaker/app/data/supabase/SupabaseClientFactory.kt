package com.goalmaker.app.data.supabase

import com.goalmaker.app.application.environment.BackendEnvironment
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.realtime.Realtime
import io.github.jan.supabase.storage.Storage

/** Builds the one Supabase client for the chosen backend. Called only by the composition root. */
object SupabaseClientFactory {
    const val RELEASES_BUCKET = "releases"

    fun create(environment: BackendEnvironment): SupabaseClient =
        createSupabaseClient(supabaseUrl = environment.url, supabaseKey = environment.publishableKey) {
            install(Auth)
            install(Storage)
            install(Realtime)
        }
}
