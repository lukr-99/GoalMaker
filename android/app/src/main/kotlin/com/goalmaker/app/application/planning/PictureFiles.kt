package com.goalmaker.app.application.planning

/**
 * This device's cache of life goal picture files (ADR 0018), by picture id, with a mark on the ones
 * still to upload. The rows are in the replica; only the JPEG bytes are here.
 */
interface PictureFiles {
    fun has(id: String): Boolean

    fun read(id: String): ByteArray?

    /** Keeps [bytes] as picture [id]; with [pending], it waits for an upload. */
    fun write(id: String, bytes: ByteArray, pending: Boolean)

    fun delete(id: String)

    /** Every picture this device holds a file for. */
    fun ids(): Set<String>

    /** The pictures added here that have not gone up yet. */
    fun pending(): Set<String>

    fun uploaded(id: String)
}
