package com.goalmaker.app.application.planning

/**
 * The life-goal-pictures bucket (ADR 0018), as the signed-in owner, at `<owner>/<id>.jpg`. A call
 * that can't reach the server throws RemoteUnavailableException, like the sync's.
 */
interface PictureCloud {
    suspend fun upload(owner: String, id: String, bytes: ByteArray)

    /** The file's bytes, or null when the bucket has no such file. */
    suspend fun download(owner: String, id: String): ByteArray?

    /** Removes the file; one that is already gone is fine. */
    suspend fun remove(owner: String, id: String)
}
