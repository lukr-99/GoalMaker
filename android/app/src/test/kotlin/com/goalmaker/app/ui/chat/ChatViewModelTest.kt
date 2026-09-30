package com.goalmaker.app.ui.chat

import android.app.Application
import android.content.Context
import androidx.core.content.edit
import com.goalmaker.app.application.assistant.AssistantClient
import com.goalmaker.app.application.assistant.AssistantReply
import com.goalmaker.app.application.assistant.ChatMessage
import com.goalmaker.app.application.assistant.ChatRole
import com.goalmaker.app.application.auth.AuthSession
import com.goalmaker.app.application.sync.SyncState
import com.goalmaker.app.application.sync.SyncStatus
import com.goalmaker.app.data.settings.SharedPreferencesSettingsStore
import com.goalmaker.app.domain.settings.ComposerMode
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/** The composer's switch and the quick chat over a fake assistant (spec, "Quick chat (M7)"). */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class ChatViewModelTest {
    /** Answers from a script, or holds a request until [hold] completes; keeps every thread it was sent. */
    private class FakeAssistant : AssistantClient {
        val threads = mutableListOf<List<ChatMessage>>()
        val replies = ArrayDeque<AssistantReply>()
        var hold: CompletableDeferred<AssistantReply>? = null

        override suspend fun ask(thread: List<ChatMessage>): AssistantReply {
            threads += thread
            return hold?.await() ?: replies.removeFirst()
        }
    }

    private val dispatcher = UnconfinedTestDispatcher()
    private val assistant = FakeAssistant()
    private val session = MutableStateFlow<AuthSession>(AuthSession.SignedIn("owner", "owner@example.com"))
    private val syncStatus = MutableStateFlow(SyncStatus.INITIAL)
    private var syncs = 0

    private val preferences get() =
        RuntimeEnvironment.getApplication().getSharedPreferences("chat-test", Context.MODE_PRIVATE)

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        preferences.edit(commit = true) { clear() }
    }

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun viewModel() = ChatViewModel(assistant, SharedPreferencesSettingsStore(preferences), session, syncStatus) { syncs++ }

    private fun chatting() = viewModel().also { it.choose(ComposerMode.CHAT) }

    @Test
    fun `the composer quick-adds until chat is picked, and the pick is remembered on the device`() {
        val first = viewModel()
        assertEquals(ComposerMode.QUICK_ADD, first.uiState.value.chosen)
        assertFalse(first.uiState.value.chatting)
        assertFalse(first.send("what's left today?"))
        assertTrue(assistant.threads.isEmpty())

        first.choose(ComposerMode.CHAT)
        assertTrue(first.uiState.value.chatting)

        // The next start comes back to chat.
        val again = viewModel()
        assertEquals(ComposerMode.CHAT, again.uiState.value.chosen)
        assertTrue(again.uiState.value.chatting)

        again.choose(ComposerMode.QUICK_ADD)
        assertEquals(ComposerMode.QUICK_ADD, viewModel().uiState.value.chosen)
    }

    @Test
    fun `a request sends the whole thread, shows the answer and syncs`() {
        val chat = chatting()
        assistant.replies += AssistantReply.Answer("Added Call the bank for tomorrow at 9.")
        assistant.replies += AssistantReply.Answer("Two tasks are left today.")

        assertTrue(chat.send("  add call the bank tomorrow at 9 "))
        assertTrue(chat.send("what's left today?"))

        assertEquals(
            listOf(
                ChatMessage(ChatRole.USER, "add call the bank tomorrow at 9"),
                ChatMessage(ChatRole.MODEL, "Added Call the bank for tomorrow at 9."),
                ChatMessage(ChatRole.USER, "what's left today?"),
            ),
            assistant.threads.last(),
        )
        assertEquals(1, assistant.threads.first().size)
        val lines = chat.uiState.value.lines
        assertEquals(4, lines.size)
        assertEquals(ChatMessage(ChatRole.MODEL, "Two tasks are left today."), lines.last().message)
        assertTrue(lines.all { it.problem == null })
        assertFalse(chat.uiState.value.thinking)
        // Each answer brings in what the chat changed.
        assertEquals(2, syncs)
    }

    @Test
    fun `it thinks while a request runs, and takes no second line meanwhile`() {
        val chat = chatting()
        val answer = CompletableDeferred<AssistantReply>()
        assistant.hold = answer

        assertTrue(chat.send("move everything from today to Friday"))

        assertTrue(chat.uiState.value.thinking)
        assertFalse(chat.send("and tomorrow too"))
        answer.complete(AssistantReply.Answer("Moved 3 tasks to Friday."))

        assertFalse(chat.uiState.value.thinking)
        assertEquals(2, chat.uiState.value.lines.size)
        assertEquals(1, assistant.threads.size)
    }

    @Test
    fun `an error stays under the line, which is not sent again, and try again resends it`() {
        val chat = chatting()
        assistant.replies += AssistantReply.RateLimited

        chat.send("what's on today?")

        val failed = chat.uiState.value.lines.single()
        assertEquals(ChatProblem.RATE_LIMITED, failed.problem)
        assertTrue(chat.uiState.value.chatting)
        assertEquals(0, syncs)

        assistant.replies += AssistantReply.Answer("Nothing yet.")
        chat.send("hello?")
        assertEquals(listOf(ChatMessage(ChatRole.USER, "hello?")), assistant.threads.last())

        assistant.replies += AssistantReply.Answer("Still nothing.")
        chat.retry(failed.id)
        assertEquals(ChatMessage(ChatRole.USER, "what's on today?"), assistant.threads.last().last())
        assertEquals(3, assistant.threads.last().size)
        assertTrue(chat.uiState.value.lines.all { it.problem == null })
    }

    @Test
    fun `every server error has its own reason`() {
        val chat = chatting()
        val cases = listOf(
            AssistantReply.RateLimited to ChatProblem.RATE_LIMITED,
            AssistantReply.ProviderLimit to ChatProblem.PROVIDER_LIMIT,
            AssistantReply.BadRequest to ChatProblem.BAD_REQUEST,
            AssistantReply.Failed to ChatProblem.FAILED,
            AssistantReply.SignedOut to ChatProblem.SIGNED_OUT,
        )
        cases.forEach { (reply, problem) ->
            assistant.replies += reply
            chat.send("line")
            assertEquals(problem, chat.uiState.value.lines.last().problem)
        }
    }

    @Test
    fun `offline, chat says why and the composer quick-adds until a sync gets through`() {
        val chat = chatting()
        assistant.replies += AssistantReply.Offline

        chat.send("what's on today?")

        assertEquals(ChatProblem.OFFLINE, chat.uiState.value.lines.single().problem)
        assertEquals(ChatAvailability.OFFLINE, chat.uiState.value.availability)
        assertTrue(chat.uiState.value.chatBlocked)
        assertFalse(chat.uiState.value.chatting)
        assertFalse(chat.send("again"))
        // Sync is asked to try, and its success opens chat again.
        assertEquals(1, syncs)
        syncStatus.value = SyncStatus(SyncState.SYNCING, null, 0, null)
        syncStatus.value = SyncStatus(SyncState.IDLE, null, 0, null)
        assertTrue(chat.uiState.value.chatting)
    }

    @Test
    fun `while sync is offline chat is not available`() {
        syncStatus.value = SyncStatus(SyncState.OFFLINE, null, 2, "offline")
        val chat = chatting()

        assertEquals(ChatAvailability.OFFLINE, chat.uiState.value.availability)
        assertFalse(chat.send("hello"))
        assertTrue(assistant.threads.isEmpty())
    }

    @Test
    fun `signed out, chat is not available`() {
        session.value = AuthSession.SignedOut
        val chat = chatting()

        assertEquals(ChatAvailability.SIGNED_OUT, chat.uiState.value.availability)
        assertFalse(chat.send("hello"))
    }

    @Test
    fun `no key on the server closes chat for this run`() {
        val chat = chatting()
        assistant.replies += AssistantReply.Unavailable

        chat.send("hello")

        assertEquals(ChatAvailability.NOT_SET_UP, chat.uiState.value.availability)
        assertEquals(ChatProblem.NOT_SET_UP, chat.uiState.value.lines.single().problem)
        assertFalse(chat.send("hello again"))
        // The pick itself is kept for when the server has a key.
        assertEquals(ComposerMode.CHAT, chat.uiState.value.chosen)
    }

    @Test
    fun `clear empties the thread and drops a request still running`() {
        val chat = chatting()
        assistant.replies += AssistantReply.Answer("Hi.")
        chat.send("hello")
        assistant.hold = CompletableDeferred()
        chat.send("what's on today?")
        assertTrue(chat.uiState.value.thinking)
        val before = syncs

        chat.clear()

        assertTrue(chat.uiState.value.lines.isEmpty())
        assertFalse(chat.uiState.value.thinking)
        // What the dropped request may have changed still comes in.
        assertEquals(before + 1, syncs)

        // The next request starts a new thread.
        assistant.hold = null
        assistant.replies += AssistantReply.Answer("Fresh start.")
        chat.send("hello")
        assertEquals(listOf(ChatMessage(ChatRole.USER, "hello")), assistant.threads.last())
        assertNull(chat.uiState.value.lines.last().problem)
    }
}
