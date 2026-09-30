package com.goalmaker.app.ui.chat

/** Why a line the owner sent got no answer, shown under that line. */
enum class ChatProblem {
    OFFLINE,
    SIGNED_OUT,
    NOT_SET_UP,
    RATE_LIMITED,
    PROVIDER_LIMIT,
    BAD_REQUEST,
    FAILED,
}
