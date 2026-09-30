package com.goalmaker.app.application.assistant

/** Who said a line in the chat: the owner or the model. [wire] is the name the assistant call uses. */
enum class ChatRole(val wire: String) {
    USER("user"),
    MODEL("model"),
}
