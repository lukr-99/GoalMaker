package com.goalmaker.app.ui.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.goalmaker.app.application.assistant.AssistantClient
import com.goalmaker.app.application.assistant.AssistantReply
import com.goalmaker.app.application.assistant.ChatMessage
import com.goalmaker.app.application.assistant.ChatRole
import com.goalmaker.app.application.auth.AuthSession
import com.goalmaker.app.application.settings.SettingsStore
import com.goalmaker.app.application.sync.SyncState
import com.goalmaker.app.application.sync.SyncStatus
import com.goalmaker.app.domain.settings.ComposerMode
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * The composer's switch between quick-add and chat, and the chat itself (spec, "Quick chat (M7)").
 * The switch is remembered in [settings]; the thread lives only here, in memory, and is gone when the
 * app closes. Each request sends the whole thread to [assistant]; after an answer [syncNow] brings
 * in what the chat changed.
 *
 * Chat can't run offline (the sync is offline, or the last request could not reach the server),
 * signed out (per [session]), or when the server said it has no model key; the composer then
 * quick-adds and says why.
 */
class ChatViewModel(
    private val assistant: AssistantClient,
    private val settings: SettingsStore,
    session: Flow<AuthSession>,
    syncStatus: StateFlow<SyncStatus>,
    private val syncNow: suspend () -> Unit,
) : ViewModel() {

    private val lines = MutableStateFlow(emptyList<ChatLine>())
    private val thinking = MutableStateFlow(false)
    private val notSetUp = MutableStateFlow(false)

    // A request that could not reach the server; a later sync that does reach it clears this.
    private val unreachable = MutableStateFlow(false)
    private var running: Job? = null
    private var nextId = 0L

    private val availability = combine(session, syncStatus, unreachable, notSetUp) { owner, status, lost, noKey ->
        when {
            owner !is AuthSession.SignedIn -> ChatAvailability.SIGNED_OUT
            noKey -> ChatAvailability.NOT_SET_UP
            lost || status.state == SyncState.OFFLINE -> ChatAvailability.OFFLINE
            else -> ChatAvailability.AVAILABLE
        }
    }

    val uiState: StateFlow<ChatUiState> = combine(settings.composerMode, availability, lines, thinking, ::ChatUiState)
        .stateIn(viewModelScope, SharingStarted.Eagerly, ChatUiState(chosen = settings.composerMode.value))

    init {
        viewModelScope.launch {
            // Only a change counts: the state at the moment of a failed request says nothing new.
            syncStatus.collect { status -> if (status.state == SyncState.IDLE) unreachable.value = false }
        }
    }

    /** Picks quick-add or chat and remembers it on this device. */
    fun choose(mode: ComposerMode) = settings.setComposerMode(mode)

    /**
     * Sends [text] with the thread so far. False when nothing went out (chat can't run, a request is
     * still running, or the line is empty), so the composer keeps its text.
     */
    fun send(text: String): Boolean {
        val line = text.trim()
        if (line.isEmpty() || !uiState.value.chatting || running?.isActive == true) return false
        val asked = ChatLine(nextId++, ChatMessage(ChatRole.USER, line))
        lines.update { it + asked }
        // Lines that got no answer stay on screen but are not sent again.
        val thread = lines.value.filter { it.problem == null }.map(ChatLine::message)
        thinking.value = true
        running = viewModelScope.launch {
            try {
                answer(asked, assistant.ask(thread))
            } finally {
                thinking.value = false
            }
        }
        return true
    }

    /** Sends a line that got no answer again, in its place at the end of the thread. */
    fun retry(id: Long) {
        val failed = lines.value.firstOrNull { it.id == id && it.problem != null } ?: return
        if (!uiState.value.chatting || running?.isActive == true) return
        lines.update { all -> all.filterNot { it.id == id } }
        send(failed.message.text)
    }

    /** Empties the thread; a request still running is dropped. */
    fun clear() {
        val wasRunning = running?.isActive == true
        running?.cancel()
        running = null
        lines.value = emptyList()
        thinking.value = false
        // The server may have acted on a request dropped halfway.
        if (wasRunning) viewModelScope.launch { syncNow() }
    }

    private fun answer(asked: ChatLine, reply: AssistantReply) {
        val problem = when (reply) {
            is AssistantReply.Answer -> {
                lines.update { it + ChatLine(nextId++, ChatMessage(ChatRole.MODEL, reply.text)) }
                // The changes it made come in through sync like any other, and show at once.
                viewModelScope.launch { syncNow() }
                return
            }
            AssistantReply.Offline -> ChatProblem.OFFLINE.also {
                unreachable.value = true
                // Sync keeps trying on its own while offline, and its next success opens chat again.
                viewModelScope.launch { syncNow() }
            }
            AssistantReply.SignedOut -> ChatProblem.SIGNED_OUT
            AssistantReply.Unavailable -> ChatProblem.NOT_SET_UP.also { notSetUp.value = true }
            AssistantReply.RateLimited -> ChatProblem.RATE_LIMITED
            AssistantReply.ProviderLimit -> ChatProblem.PROVIDER_LIMIT
            AssistantReply.BadRequest -> ChatProblem.BAD_REQUEST
            AssistantReply.Failed -> ChatProblem.FAILED
        }
        lines.update { all -> all.map { if (it.id == asked.id) it.copy(problem = problem) else it } }
    }
}
