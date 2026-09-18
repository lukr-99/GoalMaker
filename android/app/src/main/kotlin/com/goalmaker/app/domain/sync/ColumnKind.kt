package com.goalmaker.app.domain.sync

import java.util.Locale

/** How a synced column is stored in the replica and sent on the wire (contracts/schemas/synced-tables.json). */
enum class ColumnKind {
    TEXT,
    INTEGER,
    REAL,
    BOOLEAN,
    TIMESTAMP,
    DATE,
    TIME,
    ;

    companion object {
        fun parse(value: String): ColumnKind = valueOf(value.uppercase(Locale.ROOT))
    }
}
