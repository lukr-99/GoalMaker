package com.goalmaker.app.application.sync

/**
 * The server refused one row or call (a policy or constraint). The rest of the sync goes on.
 * [code] is the database's SQLSTATE when the server named one, so callers can tell refusals apart.
 */
class RemoteRejectedException(message: String, val code: String? = null) : Exception(message)
