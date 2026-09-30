package com.goalmaker.app.application.assistant

/** One line of the chat thread, as the assistant call carries it. */
data class ChatMessage(val role: ChatRole, val text: String)
