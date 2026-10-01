package com.goalmaker.app.ui.composer

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.clearText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.goalmaker.app.R
import com.goalmaker.app.domain.settings.ComposerMode
import com.goalmaker.app.ui.chat.ChatThread
import com.goalmaker.app.ui.chat.ChatUnavailableNote
import com.goalmaker.app.ui.chat.ChatViewModel
import com.goalmaker.app.ui.chat.ComposerModeSwitch

/**
 * The bottom bar of Today, Tomorrow, the Inbox, Wants, Habits and Goals (docs/composer.md, "The
 * bottom bar on every list"): the composer with its switch to the quick chat, the chat's short thread
 * above it, and why chat can't run when it can't. In the chat the line goes to [chat]; otherwise
 * [onAdd] adds what it says, with [chips] previewing it, and the plus opens the page's form
 * ([onOpenForm], named [formLabel]). The caller keeps it clear of the navigation bar and the keyboard
 * through [modifier].
 */
@Composable
fun BottomComposer(
    state: TextFieldState,
    chat: ChatViewModel,
    chips: List<ComposerChip>,
    canAdd: Boolean,
    onAdd: () -> Unit,
    placeholder: String,
    addLabel: String,
    formLabel: String,
    onOpenForm: () -> Unit,
    modifier: Modifier = Modifier,
    onRemove: ((ComposerChip) -> Unit)? = null,
) {
    val chatState by chat.uiState.collectAsStateWithLifecycle()
    val chatting = chatState.chatting
    val line = state.text.toString()
    Column(modifier) {
        // Picked chat but it can't run: say why, and the bar adds meanwhile.
        if (chatState.chatBlocked) ChatUnavailableNote(chatState.availability)
        if (chatState.chosen == ComposerMode.CHAT && (chatState.lines.isNotEmpty() || chatState.thinking)) {
            ChatThread(chatState, onClear = chat::clear, onRetry = chat::retry)
        }
        // One bar for both modes, so switching keeps the line and the keyboard.
        ComposerBar(
            state = state,
            chips = if (chatting) emptyList() else chips,
            canSend = if (chatting) line.isNotBlank() && !chatState.thinking else canAdd,
            onSubmit = { submitLine(line, chatting, send = chat::send, add = onAdd, clear = { state.clearText() }) },
            onRemove = onRemove,
            placeholder = if (chatting) stringResource(R.string.chat_placeholder) else placeholder,
            sendLabel = if (chatting) stringResource(R.string.chat_send) else addLabel,
            leading = { ComposerModeSwitch(chatState, chat::choose) },
            onOpenForm = onOpenForm,
            formLabel = formLabel,
            chatting = chatting,
        )
    }
}

/**
 * Where a sent line goes: in the quick chat to [send] (the line clears once it went out), otherwise to
 * the page's [add], which clears the line itself when it added something.
 */
internal fun submitLine(line: String, chatting: Boolean, send: (String) -> Boolean, add: () -> Unit, clear: () -> Unit) {
    if (!chatting) {
        add()
    } else if (send(line)) {
        clear()
    }
}
