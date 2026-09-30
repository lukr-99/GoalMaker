package com.goalmaker.app.application.assistant

/**
 * The quick chat's port (spec, "Quick chat (M7)"): sends the whole thread so far, the owner's line
 * last, to the `assistant` function as the signed-in owner and returns how it came back. It never
 * throws for a failure the owner can meet; each one is an [AssistantReply].
 */
fun interface AssistantClient {
    suspend fun ask(thread: List<ChatMessage>): AssistantReply
}
