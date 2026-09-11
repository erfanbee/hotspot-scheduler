package com.iranjan.hotspotscheduler.accessibility

import android.accessibilityservice.AccessibilityService
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class HotspotAccessibilityService : AccessibilityService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    override fun onServiceConnected() {
        super.onServiceConnected()
        AccessibilityServiceHolder.service = this
        Log.i(TAG, "accessibility service connected")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        event ?: return
        val pkg = event.packageName?.toString() ?: return
        if (pkg != SETTINGS_PACKAGE) return
        if (AccessibilityServiceHolder.calibrationMode &&
            event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
        ) {
            scope.launch { emitCalibrationDump() }
        }
    }

    override fun onInterrupt() {}

    override fun onDestroy() {
        super.onDestroy()
        if (AccessibilityServiceHolder.service === this) {
            AccessibilityServiceHolder.service = null
        }
        scope.cancel()
        Log.i(TAG, "accessibility service destroyed")
    }

    fun rootNode(): AccessibilityNodeInfo? = rootInActiveWindow

    suspend fun emitCalibrationDump() = withContext(Dispatchers.Main) {
        val dumps = NodeDumper.dump(rootInActiveWindow)
        AccessibilityServiceHolder.calibrationDumps.value = dumps
        Log.i(TAG, "calibration dump captured: ${dumps.size} nodes")
    }

    companion object {
        const val SETTINGS_PACKAGE = "com.android.settings"
        private const val TAG = "HSAuto"
    }
}
