package com.goalmaker.app.application.planning

import com.goalmaker.app.application.sync.Replica
import com.goalmaker.app.domain.sync.SyncedTable
import java.util.Locale
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.doubleOrNull

/**
 * The owner's projects and their milestones, read from the replica and changed through its outbox
 * (docs/projects.md). The items themselves are tasks, so [TaskList] moves them around the board.
 * The methods block on disk, so callers run them off the main thread.
 */
class ProjectList(
    private val replica: Replica,
    private val rows: NewRows,
    private val requestSync: () -> Unit,
) {
    /** Every project that isn't deleted, active ones first, in the owner's order. */
    fun all(): List<ProjectItem> = replica.all(TABLE).map(::toItem).filterNot(ProjectItem::deleted).sortedWith(ORDER)

    fun find(id: String): ProjectItem? = replica.get(TABLE, id)?.let(::toItem)?.takeUnless(ProjectItem::deleted)

    /** The projects with every milestone that isn't deleted. */
    fun read(): ProjectData = ProjectData(
        projects = all(),
        milestones = replica.all(MILESTONES).map(::toMilestone).filterNot(ProjectMilestone::deleted)
            .sortedWith(compareBy(ProjectMilestone::position).thenBy { it.name.lowercase(Locale.ROOT) }),
    )

    /** [read], again after every change to a project or a milestone. Collect it off the main thread. */
    fun watch(): Flow<ProjectData> = combine(replica.watch(TABLE), replica.watch(MILESTONES)) { _, _ -> read() }

    /** Adds a project at the end. Null when it has no name. */
    fun add(draft: ProjectDraft): ProjectItem? {
        val clean = check(draft) ?: return null
        val position = (all().maxOfOrNull(ProjectItem::position) ?: -1.0) + 1.0
        val row = rows.create(TABLE, values(clean) + ("position" to JsonPrimitive(position))) ?: return null
        replica.queue(TABLE, row)
        requestSync()
        return toItem(row)
    }

    /** Changes a project to what [draft] says. False when the draft has no name or the project is gone. */
    fun update(id: String, draft: ProjectDraft): Boolean {
        val clean = check(draft) ?: return false
        return change(TABLE, id) { values -> values.putAll(values(clean)) }
    }

    /** Marks a project active, paused or done. */
    fun setStatus(id: String, status: String): Boolean {
        if (status !in setOf(ProjectRules.ACTIVE, ProjectRules.PAUSED, ProjectRules.PROJECT_DONE)) return false
        return change(TABLE, id) { values -> values["status"] = JsonPrimitive(status) }
    }

    /** Deletes a project softly; its items stay as plain tasks. */
    fun delete(id: String): Boolean = change(TABLE, id) { values ->
        values[SyncedTable.DELETED_AT] = JsonPrimitive(rows.timestamp())
    }

    /** Adds a milestone at the end of a project's list. Null when it has no name or the project is gone. */
    fun addMilestone(projectId: String, name: String): ProjectMilestone? {
        val clean = name.trim().take(MAX_NAME)
        if (clean.isEmpty() || find(projectId) == null) return null
        val position = (read().milestonesOf(projectId).maxOfOrNull(ProjectMilestone::position) ?: -1.0) + 1.0
        val row = rows.create(
            MILESTONES,
            mapOf(
                "project_id" to JsonPrimitive(projectId),
                "name" to JsonPrimitive(clean),
                "position" to JsonPrimitive(position),
            ),
        ) ?: return null
        replica.queue(MILESTONES, row)
        requestSync()
        return toMilestone(row)
    }

    fun renameMilestone(id: String, name: String): Boolean {
        val clean = name.trim().take(MAX_NAME)
        if (clean.isEmpty()) return false
        return change(MILESTONES, id) { values -> values["name"] = JsonPrimitive(clean) }
    }

    /** Deletes a milestone softly; the items that carried it keep their column. */
    fun deleteMilestone(id: String): Boolean = change(MILESTONES, id) { values ->
        values[SyncedTable.DELETED_AT] = JsonPrimitive(rows.timestamp())
    }

    private fun check(draft: ProjectDraft): ProjectDraft? {
        val name = draft.name.trim().take(MAX_NAME)
        if (name.isEmpty()) return null
        val status = draft.status.takeIf {
            it in setOf(ProjectRules.ACTIVE, ProjectRules.PAUSED, ProjectRules.PROJECT_DONE)
        } ?: ProjectRules.ACTIVE
        return draft.copy(
            name = name,
            description = draft.description.take(MAX_DESCRIPTION),
            status = status,
            repositoryUrl = draft.repositoryUrl?.trim()?.take(MAX_LINK)?.takeIf(String::isNotEmpty),
            localFolder = draft.localFolder?.trim()?.take(MAX_LINK)?.takeIf(String::isNotEmpty),
            notes = draft.notes.take(MAX_NOTES),
        )
    }

    private fun values(draft: ProjectDraft): Map<String, JsonElement> = mapOf(
        "name" to JsonPrimitive(draft.name),
        "description" to JsonPrimitive(draft.description),
        "area_id" to (draft.areaId?.let(::JsonPrimitive) ?: JsonNull),
        "status" to JsonPrimitive(draft.status),
        "repository_url" to (draft.repositoryUrl?.let(::JsonPrimitive) ?: JsonNull),
        "local_folder" to (draft.localFolder?.let(::JsonPrimitive) ?: JsonNull),
        "notes" to JsonPrimitive(draft.notes),
    )

    private fun change(table: String, id: String, edit: (MutableMap<String, JsonElement>) -> Unit): Boolean {
        val row = replica.get(table, id)?.takeIf { it[SyncedTable.DELETED_AT].let { value -> value == null || value == JsonNull } }
            ?: return false
        val values = LinkedHashMap(row)
        edit(values)
        replica.queue(table, JsonObject(values))
        requestSync()
        return true
    }

    private fun toItem(row: JsonObject) = ProjectItem(
        id = row.text(SyncedTable.ID).orEmpty(),
        name = row.text("name").orEmpty(),
        description = row.text("description").orEmpty(),
        areaId = row.text("area_id"),
        status = row.text("status") ?: ProjectRules.ACTIVE,
        repositoryUrl = row.text("repository_url"),
        localFolder = row.text("local_folder"),
        notes = row.text("notes").orEmpty(),
        position = (row["position"] as? JsonPrimitive)?.doubleOrNull ?: 0.0,
        deleted = row.text(SyncedTable.DELETED_AT) != null,
    )

    private fun toMilestone(row: JsonObject) = ProjectMilestone(
        id = row.text(SyncedTable.ID).orEmpty(),
        projectId = row.text("project_id").orEmpty(),
        name = row.text("name").orEmpty(),
        position = (row["position"] as? JsonPrimitive)?.doubleOrNull ?: 0.0,
        deleted = row.text(SyncedTable.DELETED_AT) != null,
    )

    private companion object {
        const val TABLE = "projects"
        const val MILESTONES = "project_milestones"
        const val MAX_NAME = 120
        const val MAX_DESCRIPTION = 2000
        const val MAX_LINK = 500
        const val MAX_NOTES = 20_000
        val ORDER = compareBy<ProjectItem> { it.status == ProjectRules.PROJECT_DONE }
            .thenBy { it.status == ProjectRules.PAUSED }
            .thenBy(ProjectItem::position)
            .thenBy { it.name.lowercase(Locale.ROOT) }

        fun JsonObject.text(name: String): String? = (this[name] as? JsonPrimitive)?.takeUnless { it == JsonNull }?.content
    }
}
