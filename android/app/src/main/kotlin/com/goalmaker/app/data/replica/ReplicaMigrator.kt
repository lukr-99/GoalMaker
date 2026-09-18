package com.goalmaker.app.data.replica

import android.content.res.AssetManager
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL

/**
 * Applies replica/migrations the way tools/migrations.py does (ADR 0007): a schema_migrations record
 * with each file's SHA-256, every file in its own transaction, and a refusal when an applied file
 * changed or is missing.
 */
object ReplicaMigrator {
    /** Where the build puts the repository's replica/migrations (app/build.gradle.kts). */
    const val ASSET_FOLDER = "replica"

    /** The migrations packaged with this app, in order. */
    fun builtIn(assets: AssetManager): List<ReplicaMigration> =
        assets.list(ASSET_FOLDER).orEmpty()
            .filter { it.endsWith(".sql") }
            .map { name -> ReplicaMigration.fromBytes(name, assets.open("$ASSET_FOLDER/$name").use { it.readBytes() }) }
            .sortedBy(ReplicaMigration::number)

    fun apply(connection: SQLiteConnection, migrations: List<ReplicaMigration>) {
        migrations.forEachIndexed { index, migration ->
            check(migration.number == index + 1) { "Replica migrations must run 0001..N without gaps." }
        }

        connection.execSQL("PRAGMA foreign_keys = ON")
        connection.execSQL(
            """
            CREATE TABLE IF NOT EXISTS schema_migrations (
                number INTEGER PRIMARY KEY,
                filename TEXT NOT NULL UNIQUE,
                checksum_sha256 TEXT NOT NULL,
                applied_at_utc TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP
            )
            """.trimIndent(),
        )

        val applied = mutableMapOf<Int, Pair<String, String>>()
        connection.prepare("SELECT number, filename, checksum_sha256 FROM schema_migrations ORDER BY number").use { query ->
            while (query.step()) {
                applied[query.getLong(0).toInt()] = query.getText(1) to query.getText(2)
            }
        }

        val known = migrations.associateBy(ReplicaMigration::number)
        for ((number, record) in applied) {
            val migration = known[number]
                ?: error("The replica has migration %04d (%s), which this app doesn't know.".format(number, record.first))
            check(record.first == migration.name && record.second == migration.checksum) {
                "Applied migration ${migration.name} differs from the built-in file."
            }
        }

        for (migration in migrations.filter { it.number !in applied }) {
            connection.execSQL("BEGIN IMMEDIATE TRANSACTION")
            try {
                SqlScript.split(migration.sql).forEach(connection::execSQL)
                connection.prepare("INSERT INTO schema_migrations(number, filename, checksum_sha256) VALUES (?, ?, ?)").use { insert ->
                    insert.bindLong(1, migration.number.toLong())
                    insert.bindText(2, migration.name)
                    insert.bindText(3, migration.checksum)
                    insert.step()
                }
                connection.execSQL("COMMIT")
            } catch (error: Throwable) {
                connection.execSQL("ROLLBACK")
                throw error
            }
        }
    }
}
