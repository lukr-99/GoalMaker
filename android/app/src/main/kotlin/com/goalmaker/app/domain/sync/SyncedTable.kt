package com.goalmaker.app.domain.sync

/** A table both apps replicate, with its columns in contract order. */
data class SyncedTable(
    val name: String,
    val columns: List<SyncedColumn>,
) {
    /** The columns the server needs a value in, so a new row has to carry all of them. */
    val required: List<String> = columns.filter(SyncedColumn::required).map(SyncedColumn::name)

    companion object {
        const val ID = "id"
        const val OWNER_ID = "owner_id"
        const val CREATED_AT = "created_at"
        const val UPDATED_AT = "updated_at"
        const val DELETED_AT = "deleted_at"
    }
}
