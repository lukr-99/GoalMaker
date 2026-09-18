package com.goalmaker.app.application.sync

/** The server refused one row (a policy or constraint). The rest of the sync goes on. */
class RemoteRejectedException(message: String) : Exception(message)
