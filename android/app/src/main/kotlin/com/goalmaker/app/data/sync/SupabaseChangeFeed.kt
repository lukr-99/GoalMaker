package com.goalmaker.app.data.sync

import com.goalmaker.app.domain.sync.SyncedTableCatalog
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.realtime.PostgresAction
import io.github.jan.supabase.realtime.RealtimeChannel
import io.github.jan.supabase.realtime.channel
import io.github.jan.supabase.realtime.postgresChangeFlow
import io.github.jan.supabase.realtime.realtime
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Listens to Supabase Realtime for changes to the synced tables while the app is visible, and
 * reports each as a nudge; the payload is never applied, the next pull fetches the rows
 * (docs/sync.md). Every (re)join is a nudge too, since changes made while disconnected produced no
 * events. Failures are quiet: sync still runs on writes, on start and in the background.
 */
class SupabaseChangeFeed(
    private val client: SupabaseClient,
    private val catalog: SyncedTableCatalog,
    private val scope: CoroutineScope,
    private val onChange: () -> Unit,
) {
    private val lock = Any()
    private var listening: Job? = null

    fun start() {
        synchronized(lock) {
            if (listening?.isActive != true) {
                listening = scope.launch { listen() }
            }
        }
    }

    fun stop() {
        synchronized(lock) {
            listening?.cancel()
            listening = null
        }
    }

    private suspend fun listen() {
        val channel = client.channel(CHANNEL)
        try {
            coroutineScope {
                for (synced in catalog.tables) {
                    channel.postgresChangeFlow<PostgresAction>(schema = "public") { table = synced.name }
                        .onEach { onChange() }
                        .launchIn(this)
                }
                channel.status
                    .filter { it == RealtimeChannel.Status.SUBSCRIBED }
                    .onEach { onChange() }
                    .launchIn(this)
                channel.subscribe(blockUntilSubscribed = false)
                awaitCancellation()
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            // Realtime is a speed-up only.
        } finally {
            withContext(NonCancellable) {
                runCatching { client.realtime.removeChannel(channel) }
            }
        }
    }

    private companion object {
        const val CHANNEL = "goalmaker-sync"
    }
}
