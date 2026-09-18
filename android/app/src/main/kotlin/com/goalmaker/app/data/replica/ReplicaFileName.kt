package com.goalmaker.app.data.replica

import java.security.MessageDigest

/**
 * One replica file per backend, so switching a dev build between the local stack and the cloud
 * never mixes their rows. Same naming as the Windows app: replica-<12 hex of SHA-256(url)>.db.
 */
object ReplicaFileName {
    fun forBackend(url: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(url.trim().trimEnd('/').toByteArray(Charsets.UTF_8))
        return "replica-" + digest.joinToString("") { "%02x".format(it) }.take(12) + ".db"
    }
}
