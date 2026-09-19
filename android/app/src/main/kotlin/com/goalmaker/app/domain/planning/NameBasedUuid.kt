package com.goalmaker.app.domain.planning

import java.nio.ByteBuffer
import java.security.MessageDigest
import java.util.UUID

/**
 * A UUID version 5 (RFC 9562): the same name in the same namespace always gives the same id, so two
 * devices that make "the same" row offline make one row once they sync (docs/repeating.md,
 * docs/reminders.md).
 */
object NameBasedUuid {
    fun of(namespace: String, name: String): String {
        val space = UUID.fromString(namespace)
        val bytes = ByteBuffer.allocate(16).putLong(space.mostSignificantBits).putLong(space.leastSignificantBits).array()
        val hash = MessageDigest.getInstance("SHA-1").run {
            update(bytes)
            digest(name.toByteArray(Charsets.UTF_8))
        }
        hash[6] = ((hash[6].toInt() and 0x0F) or 0x50).toByte()
        hash[8] = ((hash[8].toInt() and 0x3F) or 0x80).toByte()
        val hex = hash.take(16).joinToString("") { "%02x".format(it) }
        return "${hex.substring(0, 8)}-${hex.substring(8, 12)}-${hex.substring(12, 16)}-${hex.substring(16, 20)}-${hex.substring(20)}"
    }
}
