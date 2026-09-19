package com.goalmaker.app.ui.areas

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.goalmaker.app.application.planning.AreaList
import com.goalmaker.app.application.planning.TagList
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * The areas and tags manager (M2-11): add, rename, recolor, give an emoji, reorder, archive, restore
 * and delete areas; add, rename and delete tags. Saving reports false when the name is blank or already taken, so the
 * dialog can say so. Disk work runs on [io].
 */
class AreasViewModel(
    private val areas: AreaList,
    private val tags: TagList,
    private val io: CoroutineDispatcher,
) : ViewModel() {
    val uiState: StateFlow<AreasUiState> = combine(areas.watch().flowOn(io), tags.watch().flowOn(io)) { areaList, tagList ->
        AreasUiState(areaList, tagList, areas.palette())
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AreasUiState(palette = areas.palette()))

    /** Adds an area when [id] is null, otherwise changes it. */
    suspend fun saveArea(id: String?, name: String, emoji: String, colorId: String): Boolean = withContext(io) {
        val target = if (id == null) {
            if (name.isBlank() || areas.find(name) != null) return@withContext false
            areas.create(name)?.id ?: return@withContext false
        } else {
            if (!areas.rename(id, name)) return@withContext false
            id
        }
        areas.recolor(target, colorId)
        areas.setEmoji(target, emoji)
        true
    }

    /** Moves an area one place up ([by] -1) or down ([by] 1). */
    fun moveArea(id: String, by: Int) {
        viewModelScope.launch(io) {
            val index = areas.active().indexOfFirst { it.id == id }
            if (index >= 0) areas.move(id, index + by)
        }
    }

    fun archiveArea(id: String) {
        viewModelScope.launch(io) { areas.archive(id) }
    }

    fun restoreArea(id: String) {
        viewModelScope.launch(io) { areas.restore(id) }
    }

    fun deleteArea(id: String) {
        viewModelScope.launch(io) { areas.delete(id) }
    }

    /** Adds a tag when [id] is null, otherwise renames it. */
    suspend fun saveTag(id: String?, name: String): Boolean = withContext(io) {
        if (id != null) return@withContext tags.rename(id, name)
        val existing = tags.all().map { it.id }.toSet()
        tags.findOrCreate(name)?.let { it !in existing } ?: false
    }

    fun deleteTag(id: String) {
        viewModelScope.launch(io) { tags.delete(id) }
    }
}
