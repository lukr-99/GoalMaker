package com.goalmaker.app.application.sync

/** The server can't be reached right now (offline, timeout, 5xx, expired token). Everything stays queued. */
class RemoteUnavailableException(message: String, cause: Throwable? = null) : Exception(message, cause)
