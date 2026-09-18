package com.goalmaker.app.data.replica

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SqlScriptTest {
    @Test
    fun `statements split at semicolons`() {
        assertEquals(
            listOf("CREATE TABLE a (x TEXT);", "CREATE INDEX a_x ON a (x);"),
            SqlScript.split("CREATE TABLE a (x TEXT);\n\nCREATE INDEX a_x ON a (x);\n"),
        )
    }

    @Test
    fun `semicolons in comments and quotes do not split`() {
        val script = """
            -- a comment; with a semicolon
            /* and; another */
            CREATE TABLE a (x TEXT DEFAULT ';', "odd;name" TEXT, [b;c] TEXT);
            INSERT INTO a (x) VALUES ('it''s; fine');
        """.trimIndent()

        val statements = SqlScript.split(script)

        assertEquals(2, statements.size)
        assertTrue(statements[0].endsWith("[b;c] TEXT);"))
        assertEquals("INSERT INTO a (x) VALUES ('it''s; fine');", statements[1])
    }

    @Test
    fun `a trigger body stays one statement`() {
        val trigger = """
            CREATE TEMP TRIGGER stamp AFTER UPDATE ON a
            BEGIN
                UPDATE a SET x = 'changed' WHERE rowid = new.rowid;
                DELETE FROM b;
            END;
        """.trimIndent()

        val statements = SqlScript.split("$trigger\nSELECT 1;")

        assertEquals(listOf(trigger, "SELECT 1;"), statements)
    }

    @Test
    fun `empty statements are dropped and a last one without a semicolon is kept`() {
        assertEquals(listOf("SELECT 1;", "SELECT 2"), SqlScript.split(";; SELECT 1; -- done\n;SELECT 2"))
    }

    @Test
    fun `every repository migration splits into statements that each end the way SQLite expects`() {
        val folder = File(System.getProperty("goalmaker.contracts")!!).parentFile!!.resolve("replica/migrations")
        val files = folder.listFiles { file -> file.extension == "sql" }.orEmpty()
        assertTrue(files.isNotEmpty())
        for (file in files) {
            val statements = SqlScript.split(file.readText())
            assertTrue(file.name, statements.isNotEmpty())
            statements.forEach { assertTrue("${file.name}: $it", it.endsWith(";")) }
        }
    }
}
