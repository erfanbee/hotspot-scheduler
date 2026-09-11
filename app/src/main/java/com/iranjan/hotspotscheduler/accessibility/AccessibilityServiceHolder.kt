package com.iranjan.hotspotscheduler.accessibility

import com.iranjan.hotspotscheduler.data.model.NodeDump
import kotlinx.coroutines.flow.MutableStateFlow

data class ToggleMatch(
    val stateNode: android.view.accessibility.AccessibilityNodeInfo,
    val clickTarget: android.view.accessibility.AccessibilityNodeInfo,
    val source: String
)

object AccessibilityServiceHolder {
    @Volatile
    var service: HotspotAccessibilityService? = null

    val calibrationDumps = MutableStateFlow<List<NodeDump>>(emptyList())

    @Volatile
    var calibrationMode: Boolean = false

    fun clearDumps() {
        calibrationDumps.value = emptyList()
    }
}
