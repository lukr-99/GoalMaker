package com.goalmaker.app.ui.composer

import android.app.Application
import android.content.Context
import androidx.core.content.edit
import com.goalmaker.app.application.assistant.AssistantClient
import com.goalmaker.app.application.assistant.AssistantReply
import com.goalmaker.app.application.assistant.ChatMessage
import com.goalmaker.app.application.auth.AuthSession
import com.goalmaker.app.application.sync.SyncStatus
import com.goalmaker.app.data.settings.SharedPreferencesSettingsStore
import com.goalmaker.app.domain.settings.ComposerMode
import com.goalmaker.app.ui.chat.ChatViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/** The bottom bar's round button and where a sent line goes (docs/composer.md, "The bottom bar on every list"). */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class BottomBarTest {
    private class FakeAssistant : AssistantClient {
        val threads = mutableListOf<List<ChatMessage>>()

        override suspend fun ask(thread: List<ChatMessage>): AssistantReply {
            threads += thread
            return AssistantReply.Answer("Done.")
        }
    }

    private val assistant = FakeAssistant()
    private val preferences get() =
        RuntimeEnvironment.getApplication().getSharedPreferences("bottom-bar-test", Context.MODE_PRIVATE)

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        preferences.edit(commit = true) { clear() }
    }

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun chat(mode: ComposerMode) = ChatViewModel(
        assistant,
        SharedPreferencesSettingsStore(preferences),
        MutableStateFlow(AuthSession.SignedIn("owner", "owner@example.com")),
        MutableStateFlow(SyncStatus.INITIAL),
    ) {}.also { it.choose(mode) }

    @Test
    fun `the button is a plus while the line is empty and the send arrow once something is typed`() {
        assertEquals(BarButton.PLUS, BarButton.of("", chatting = false, hasForm = true))
        assertEquals(BarButton.PLUS, BarButton.of("   ", chatting = false, hasForm = true))
        assertEquals(BarButton.SEND, BarButton.of("Swim", chatting = false, hasForm = true))
    }

    @Test
    fun `in the quick chat the button is always the send arrow`() {
        assertEquals(BarButton.SEND, BarButton.of("", chatting = true, hasForm = true))
        assertEquals(BarButton.SEND, BarButton.of("what is left?", chatting = true, hasForm = true))
    }

    @Test
    fun `a bar without a form only sends, as the quick-add box and Plan tomorrow do`() {
        assertEquals(BarButton.SEND, BarButton.of("", chatting = false, hasForm = false))
        assertEquals(BarButton.SEND, BarButton.of("Milk", chatting = false, hasForm = false))
    }

    @Test
    fun `in the quick chat a line goes to the chat and nothing is added`() {
        val chat = chat(ComposerMode.CHAT)
        var added = 0
        var cleared = 0

        submitLine("Swim 2 times a week", chat.uiState.value.chatting, send = chat::send, add = { added++ }, clear = { cleared++ })

        assertEquals(0, added)
        assertEquals(1, cleared)
        assertEquals("Swim 2 times a week", assistant.threads.single().single().text)
    }

    @Test
    fun `quick-adding, a line goes to the page and the chat hears nothing`() {
        val chat = chat(ComposerMode.QUICK_ADD)
        var added = 0
        var cleared = 0

        submitLine("Swim 2 times a week", chat.uiState.value.chatting, send = chat::send, add = { added++ }, clear = { cleared++ })

        assertEquals(1, added)
        assertEquals(0, cleared)
        assertTrue(assistant.threads.isEmpty())
    }
}
