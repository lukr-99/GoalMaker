package com.goalmaker.app.ui.places

import com.goalmaker.app.domain.navigation.DeviceKind
import com.goalmaker.app.domain.navigation.PlaceRules

/** The Places hub and the bottom bar's pins (ADR 0014); [digest] is null until the replica is read. */
data class PlacesUiState(
    val digest: PlacesDigest? = null,
    val pins: List<String> = emptyList(),
    val editing: Boolean = false,
) {
    /** The number on the Places tab: what waits in places that are not pinned. */
    val count: Int get() = digest?.let { PlaceRules.count(pins, it.waiting) } ?: 0

    /** Four pins already: an unpinned tile can't be pinned until one is unpinned. */
    val full: Boolean get() = PlaceRules.limit(DeviceKind.PHONE)?.let { pins.size >= it } ?: false
}
