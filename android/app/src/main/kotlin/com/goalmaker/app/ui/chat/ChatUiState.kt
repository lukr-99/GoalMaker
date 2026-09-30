package com.goalmaker.app.ui.chat

import com.goalmaker.app.domain.settings.ComposerMode

/**
 * The composer's mode and the chat. [chosen] is the mode the owner picked, remembered on the device;
 * [availability] says whether chat can run. [lines] is the thread, in memory only, and [thinking] is
 * true while a request runs.
 */
data class ChatUiState(
    val chosen: ComposerMode = ComposerMode.QUICK_ADD,
    val availability: ChatAvailability = ChatAvailability.AVAILABLE,
    val lines: List<ChatLine> = emptyList(),
    val thinking: Boolean = false,
) {
    /** True when the composer chats; otherwise it quick-adds, exactly as without chat. */
    val chatting: Boolean get() = chosen == ComposerMode.CHAT && availability == ChatAvailability.AVAILABLE

    /** True when the owner picked chat but it can't run, so the composer says why. */
    val chatBlocked: Boolean get() = chosen == ComposerMode.CHAT && availability != ChatAvailability.AVAILABLE
}
