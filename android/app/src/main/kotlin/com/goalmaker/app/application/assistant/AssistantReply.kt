package com.goalmaker.app.application.assistant

/**
 * How one call to the assistant came back: an [Answer], or why there is none. The server's error
 * codes (`unavailable`, `rate_limited`, `provider_limit`, `bad_request`, `failed`) each have a case;
 * [Offline] and [SignedOut] are this side of the call.
 */
sealed interface AssistantReply {
    data class Answer(val text: String) : AssistantReply

    /** No model key on the server, so chat can't run there (503 `unavailable`). */
    data object Unavailable : AssistantReply

    /** GoalMaker's own limit per owner (429 `rate_limited`). */
    data object RateLimited : AssistantReply

    /** The model's free tier is used up for now (429 `provider_limit`). */
    data object ProviderLimit : AssistantReply

    /** The server could not read the request (400 `bad_request`). */
    data object BadRequest : AssistantReply

    /** The server or the model failed (502 `failed`, or anything else unexpected). */
    data object Failed : AssistantReply

    /** The server could not be reached. */
    data object Offline : AssistantReply

    /** Nobody is signed in, or the server no longer takes the session. */
    data object SignedOut : AssistantReply
}
