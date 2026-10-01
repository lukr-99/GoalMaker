package com.goalmaker.app.data.update

import java.io.File
import java.nio.file.Files
import java.security.MessageDigest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** The private folder update files wait in: measured when asked, and cleaned of older ones. */
class UpdateFolderTest {
    private val directory: File = Files.createTempDirectory("updates").toFile()
    private val folder = UpdateFolder(directory)

    @Test
    fun `a kept file is measured by its size and SHA-256`() {
        val bytes = ByteArray(200_000) { (it % 251).toByte() }
        File(directory, "GoalMaker-1.4.0.apk").writeBytes(bytes)

        val measured = folder.measure("1.4.0/GoalMaker-1.4.0.apk")!!

        assertEquals(bytes.size.toLong(), measured.size)
        assertEquals(MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }, measured.sha256)
        assertEquals(File(directory, "GoalMaker-1.4.0.apk").absolutePath, measured.localPath)
    }

    @Test
    fun `no file and a partial one measure as nothing`() {
        File(directory, "GoalMaker-1.4.0.apk.part").writeText("half")

        assertNull(folder.measure("1.4.0/GoalMaker-1.4.0.apk"))
    }

    @Test
    fun `cleaning keeps only the file asked for`() {
        File(directory, "GoalMaker-1.3.0.apk").writeText("old")
        File(directory, "GoalMaker-1.4.0.apk.part").writeText("half")
        File(directory, "GoalMaker-1.4.0.apk").writeText("new")

        folder.clean(keepPath = "1.4.0/GoalMaker-1.4.0.apk")
        assertEquals(listOf("GoalMaker-1.4.0.apk"), directory.list()!!.toList())

        folder.clean()
        assertEquals(emptyList<String>(), directory.list()!!.toList())
    }
}
