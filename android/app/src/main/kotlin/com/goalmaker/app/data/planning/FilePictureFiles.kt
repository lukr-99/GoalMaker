package com.goalmaker.app.data.planning

import com.goalmaker.app.application.planning.PictureFiles
import java.io.File

/**
 * [PictureFiles] in a folder of private app storage: `<id>.jpg` for each picture, and an empty
 * `<id>.pending` beside one that has not gone up yet. A file is written whole under a temporary name
 * first, so a picture is never half there.
 */
class FilePictureFiles(private val folder: File) : PictureFiles {
    override fun has(id: String): Boolean = picture(id).isFile

    override fun read(id: String): ByteArray? = picture(id).takeIf(File::isFile)?.readBytes()

    override fun write(id: String, bytes: ByteArray, pending: Boolean) {
        folder.mkdirs()
        val partial = File(folder, "$id.partial")
        partial.writeBytes(bytes)
        if (pending) marker(id).createNewFile()
        if (!partial.renameTo(picture(id))) {
            picture(id).delete()
            partial.renameTo(picture(id))
        }
    }

    override fun delete(id: String) {
        picture(id).delete()
        marker(id).delete()
    }

    override fun ids(): Set<String> = names(PICTURE)

    override fun pending(): Set<String> = names(PENDING)

    override fun uploaded(id: String) {
        marker(id).delete()
    }

    private fun names(suffix: String): Set<String> =
        folder.listFiles { file -> file.name.endsWith(suffix) }.orEmpty().map { it.name.removeSuffix(suffix) }.toSet()

    private fun picture(id: String) = File(folder, check(id) + PICTURE)

    private fun marker(id: String) = File(folder, check(id) + PENDING)

    // Ids are UUIDs; anything else would be a path, which never belongs here.
    private fun check(id: String): String = id.also { require(ID.matches(it)) { "not a picture id: $it" } }

    private companion object {
        const val PICTURE = ".jpg"
        const val PENDING = ".pending"
        val ID = Regex("^[0-9a-fA-F-]{36}$")
    }
}
