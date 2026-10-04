package com.goalmaker.app.application.planning

import com.goalmaker.app.application.sync.RemoteRejectedException
import com.goalmaker.app.application.sync.RemoteUnavailableException
import java.time.Duration
import java.time.Instant
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Life goal pictures as files (docs/life-goals.md, ADR 0018): a picture is kept here at once and
 * goes up when the app can reach the server; a picture this device lacks comes down once; a deleted
 * picture's file goes a day after its row did, so an undo in between still finds it.
 */
class LifeGoalPictures(
    private val lifeGoals: LifeGoalList,
    private val files: PictureFiles,
    private val cloud: PictureCloud,
    private val owner: () -> String?,
    private val now: () -> Instant,
    private val requestTransfer: () -> Unit,
) {
    private val running = Mutex()
    private val fileChanges = MutableStateFlow(0)

    /** Counts up whenever a file comes or goes here, so a screen waiting for one looks again. */
    val changes: StateFlow<Int> = fileChanges.asStateFlow()

    /** Adds a shrunk JPEG of [width] by [height] pixels to [lifeGoalId]; null when the life goal is gone. */
    fun add(lifeGoalId: String, jpeg: ByteArray, width: Int, height: Int): LifeGoalPicture? {
        val picture = lifeGoals.addPicture(lifeGoalId, width, height) ?: return null
        files.write(picture.id, jpeg, pending = true)
        fileChanges.update { it + 1 }
        requestTransfer()
        return picture
    }

    /** The picture's bytes when this device has them. */
    fun read(id: String): ByteArray? = files.read(id)

    /**
     * Uploads what waits, downloads what is missing and removes what was deleted long enough ago.
     * False when the server could not be reached, so it is worth trying again later.
     */
    suspend fun transfer(): Boolean = running.withLock {
        val owner = owner() ?: return true
        val before = files.ids()
        val live = lifeGoals.allPictures().map(LifeGoalPicture::id).toSet()
        val tombstones = lifeGoals.pictureTombstones()
        try {
            for (id in files.pending()) {
                when {
                    id in live -> attempt { cloud.upload(owner, id, files.read(id) ?: return@attempt) }.also { if (it) files.uploaded(id) }
                    // Deleted before it went up: nothing to send.
                    id in tombstones -> files.uploaded(id)
                }
            }
            for (id in live - files.ids()) {
                attempt { cloud.download(owner, id)?.let { files.write(id, it, pending = false) } }
            }
            val cutoff = now().minus(GRACE)
            for ((id, deletedAt) in tombstones) {
                if (files.has(id) && id !in files.pending() && Instant.parse(deletedAt).isBefore(cutoff)) {
                    if (attempt { cloud.remove(owner, id) }) files.delete(id)
                }
            }
            // A row the purge took long ago leaves nothing to keep.
            (files.ids() - live - tombstones.keys - files.pending()).forEach(files::delete)
            true
        } catch (offline: RemoteUnavailableException) {
            false
        } finally {
            if (files.ids() != before) fileChanges.update { it + 1 }
        }
    }

    // One file's call; a refusal (a file too big, a policy) skips that file and leaves the rest.
    private inline fun attempt(call: () -> Unit): Boolean = try {
        call()
        true
    } catch (refused: RemoteRejectedException) {
        false
    }

    private companion object {
        val GRACE: Duration = Duration.ofDays(1)
    }
}
