package com.goalmaker.app.ui.connector

import com.goalmaker.app.application.connector.ConnectorLink
import com.goalmaker.app.application.connector.ConnectorLinks
import com.goalmaker.app.application.sync.RemoteUnavailableException
import java.time.Instant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** Creating, rotating and revoking the connector link over a fake server (docs/connector.md). */
@OptIn(ExperimentalCoroutinesApi::class)
class ConnectorViewModelTest {
    private class FakeLinks : ConnectorLinks {
        val links = mutableListOf<ConnectorLink>()
        var offline = false
        private var made = 0

        override suspend fun list(): List<ConnectorLink> {
            if (offline) throw RemoteUnavailableException("offline")
            return links.sortedByDescending { it.createdAt }
        }

        override suspend fun create(): String {
            revoke()
            made++
            links += ConnectorLink("link-$made", Instant.parse("2026-09-19T10:00:00Z").plusSeconds(made.toLong()), null, null)
            return "secret-$made"
        }

        override suspend fun revoke() {
            links.replaceAll { if (it.active) it.copy(revokedAt = Instant.parse("2026-09-19T11:00:00Z")) else it }
        }
    }

    private val links = FakeLinks()
    private val dispatcher = UnconfinedTestDispatcher()

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun viewModel() = ConnectorViewModel(links, "https://example.supabase.co/", dispatcher)

    @Test
    fun `a new link is shown once as the connector URL`() {
        val viewModel = viewModel()
        assertNull(viewModel.uiState.value.active)

        viewModel.create()

        assertEquals("https://example.supabase.co/functions/v1/connector/secret-1", viewModel.uiState.value.newUrl)
        assertEquals("link-1", viewModel.uiState.value.active?.id)
        viewModel.hideNewLink()
        assertNull(viewModel.uiState.value.newUrl)
    }

    @Test
    fun `making a new link rotates, and revoking leaves none`() {
        val viewModel = viewModel()
        viewModel.create()
        viewModel.create()

        assertEquals("link-2", viewModel.uiState.value.active?.id)
        assertEquals(1, links.links.count { it.active })

        viewModel.revoke()

        assertNull(viewModel.uiState.value.active)
        assertNull(viewModel.uiState.value.newUrl)
    }

    @Test
    fun `offline, the screen says it needs a connection`() {
        links.offline = true

        val viewModel = viewModel()

        assertTrue(viewModel.uiState.value.unavailable)
    }
}
