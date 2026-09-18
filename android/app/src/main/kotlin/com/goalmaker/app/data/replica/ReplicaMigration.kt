package com.goalmaker.app.data.replica

import java.security.MessageDigest

/** One file from replica/migrations, with the SHA-256 of its exact bytes (ADR 0007). */
data class ReplicaMigration(
    val number: Int,
    val name: String,
    val sql: String,
    val checksum: String,
) {
    companion object {
        private const val BYTE_ORDER_MARK = "\uFEFF"
        private val fileName = Regex("([0-9]{4})_[a-z][a-z0-9_]*\\.sql")

        fun fromBytes(name: String, bytes: ByteArray): ReplicaMigration {
            val match = fileName.matchEntire(name) ?: error("$name is not named 0001_description.sql")
            val digest = MessageDigest.getInstance("SHA-256").digest(bytes)
            return ReplicaMigration(
                number = match.groupValues[1].toInt(),
                name = name,
                sql = bytes.toString(Charsets.UTF_8).removePrefix(BYTE_ORDER_MARK),
                checksum = digest.joinToString("") { "%02x".format(it) },
            )
        }
    }
}
