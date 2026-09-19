package com.goalmaker.app.ui.connector

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.goalmaker.app.application.connector.ConnectorLinks
import com.goalmaker.app.application.sync.RemoteRejectedException
import com.goalmaker.app.application.sync.RemoteUnavailableException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Creates, rotates and revokes the Claude connector's link (docs/connector.md). A new link's URL is
 * kept only in this screen's state, shown once, and never written anywhere.
 */
class ConnectorViewModel(
    private val links: ConnectorLinks,
    private val backendUrl: String,
    private val io: CoroutineDispatcher,
) : ViewModel() {
    private val state = MutableStateFlow(ConnectorUiState())
    val uiState: StateFlow<ConnectorUiState> = state.asStateFlow()

    // The link the new URL belongs to, so the URL goes once that link stops being the active one.
    private var newLinkId: String? = null

    init {
        refresh()
    }

    fun refresh() = call { withContext(io) { load() } }

    /** Makes a link; when one is active, this is rotating: the old one stops working. */
    fun create() = call {
        val secret = withContext(io) { links.create() }
        newLinkId = null
        state.update { it.copy(newUrl = ConnectorLinks.url(backendUrl, secret)) }
        withContext(io) { load() }
    }

    fun revoke() = call {
        withContext(io) { links.revoke() }
        state.update { it.copy(newUrl = null) }
        withContext(io) { load() }
    }

    /** The owner copied the new link; it isn't shown again. */
    fun hideNewLink() = state.update { it.copy(newUrl = null) }

    private suspend fun load() {
        val active = links.list().firstOrNull { it.active }
        if (state.value.newUrl != null) {
            // Made just now: remember its link. Revoked or replaced elsewhere since: the URL is dead.
            if (newLinkId == null) newLinkId = active?.id
            if (active == null || active.id != newLinkId) state.update { it.copy(newUrl = null) }
        }
        state.update { it.copy(loaded = true, active = active, unavailable = false) }
    }

    private fun call(work: suspend () -> Unit) {
        if (state.value.busy) return
        viewModelScope.launch {
            state.update { it.copy(busy = true) }
            try {
                work()
            } catch (_: RemoteUnavailableException) {
                state.update { it.copy(loaded = true, unavailable = true) }
            } catch (_: RemoteRejectedException) {
                state.update { it.copy(loaded = true, unavailable = true) }
            } finally {
                state.update { it.copy(busy = false) }
            }
        }
    }
}
