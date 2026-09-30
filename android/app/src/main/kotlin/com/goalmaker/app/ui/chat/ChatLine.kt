package com.goalmaker.app.ui.chat

import com.goalmaker.app.application.assistant.ChatMessage

/**
 * A line on screen in the chat thread, with an [id] for the list. A line of the owner's that got no
 * answer carries its [problem] and is left out of what the next request sends.
 */
data class ChatLine(val id: Long, val message: ChatMessage, val problem: ChatProblem? = null)
