package com.ireddragonicy.konabessnext.model.display

import androidx.compose.runtime.Immutable

/** A numeric cell-array property (`name = <a b c>;`) inside a charging/battery DTBO node. */
@Immutable
data class ChargingProperty(
    val name: String,
    val cells: List<Long>,
    val isHex: Boolean
)

/** A charger / battery / fast-charge node found inside a DTBO overlay fragment. */
@Immutable
data class ChargingNode(
    val fragmentIndex: Int,
    /** Exact fragment node name as it appears in the DTS (e.g. "fragment@31"). */
    val fragmentName: String,
    /** Node names from `__overlay__` (exclusive) down to this node (inclusive). */
    val path: List<String>,
    val compatible: String,
    val properties: List<ChargingProperty>
) {
    val nodeName: String get() = path.lastOrNull() ?: ""
    val displayPath: String get() = path.joinToString("/")
}
