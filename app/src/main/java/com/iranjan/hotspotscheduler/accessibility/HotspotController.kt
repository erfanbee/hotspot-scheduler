package com.iranjan.hotspotscheduler.accessibility

import android.content.Context
import android.provider.Settings
import android.util.Log
import android.view.accessibility.AccessibilityNodeInfo
import com.iranjan.hotspotscheduler.data.prefs.AutomationPrefs
import com.iranjan.hotspotscheduler.service.NotificationHelper
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject
import javax.inject.Singleton

enum class ToggleResult { ALREADY_OK, TOGGLED, FAILED }

interface HotspotController {
    suspend fun readHotspotState(): Boolean?
    suspend fun setHotspotState(targetOn: Boolean): ToggleResult
    suspend fun requestCalibrationDump()
}

@Singleton
class AccessibilityHotspotControllerImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val prefs: AutomationPrefs,
    private val notifications: NotificationHelper,
    private val navigator: HotspotNavigator
) : HotspotController {

    override suspend fun readHotspotState(): Boolean? {
        val service = AccessibilityServiceHolder.service ?: return null
        val calibration = prefs.calibration()
        return withContext(Dispatchers.Main) {
            val match = NodeMatcher.findToggle(service.rootNode(), calibration) ?: return@withContext null
            NodeMatcher.readState(match)
        }
    }

    override suspend fun setHotspotState(targetOn: Boolean): ToggleResult {
        val result = withTimeoutOrNull(TOTAL_TIMEOUT_MS) { runToggle(targetOn) } ?: ToggleResult.FAILED
        if (result == ToggleResult.FAILED) {
            Log.e(TAG, "toggle failed targetOn=$targetOn accessibilityConnected=${AccessibilityServiceHolder.service != null} overlay=${Settings.canDrawOverlays(context)}")
        } else {
            prefs.setLastKnownHotspotOn(targetOn)
        }
        return result
    }

    override suspend fun requestCalibrationDump() {
        val service = AccessibilityServiceHolder.service ?: return
        service.emitCalibrationDump()
    }

    private suspend fun runToggle(targetOn: Boolean): ToggleResult {
        attempt(targetOn)?.let { return it }

        if (Settings.canDrawOverlays(context)) {
            if (!navigator.launchHotspotSettings()) {
                Log.e(TAG, "could not launch hotspot settings")
                return ToggleResult.FAILED
            }
            awaitToggleScreen(SCREEN_WAIT_MS)
            attempt(targetOn)?.let { return it }
            return ToggleResult.FAILED
        }

        notifications.postOpenHotspotSettingsPrompt()
        val deadline = System.currentTimeMillis() + MANUAL_WAIT_MS
        while (System.currentTimeMillis() < deadline) {
            attempt(targetOn)?.let { return it }
            delay(500)
        }
        return ToggleResult.FAILED
    }

    private suspend fun attempt(targetOn: Boolean): ToggleResult? {
        val calibration = prefs.calibration()
        val match = findToggle(calibration) ?: return null
        val before = withContext(Dispatchers.Main) { NodeMatcher.readState(match) } ?: return null
        if (before == targetOn) return ToggleResult.ALREADY_OK

        withContext(Dispatchers.Main) {
            match.clickTarget.performAction(AccessibilityNodeInfo.ACTION_CLICK)
        }
        if (awaitStateChange(before, calibration)) return ToggleResult.TOGGLED

        val retryMatch = findToggle(calibration)
        if (retryMatch != null) {
            val stateAfter = withContext(Dispatchers.Main) { NodeMatcher.readState(retryMatch) }
            if (stateAfter == targetOn) return ToggleResult.TOGGLED
            if (stateAfter == before) {
                withContext(Dispatchers.Main) {
                    retryMatch.clickTarget.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                }
                if (awaitStateChange(before, calibration)) return ToggleResult.TOGGLED
            }
        }
        return ToggleResult.FAILED
    }

    private suspend fun findToggle(calibration: com.iranjan.hotspotscheduler.data.model.CalibrationSignature?): ToggleMatch? {
        val service = AccessibilityServiceHolder.service ?: return null
        return withContext(Dispatchers.Main) { NodeMatcher.findToggle(service.rootNode(), calibration) }
    }

    private suspend fun awaitStateChange(
        before: Boolean,
        calibration: com.iranjan.hotspotscheduler.data.model.CalibrationSignature?
    ): Boolean {
        val deadline = System.currentTimeMillis() + STATE_TIMEOUT_MS
        while (System.currentTimeMillis() < deadline) {
            delay(300)
            val match = findToggle(calibration) ?: continue
            val state = withContext(Dispatchers.Main) { NodeMatcher.readState(match) }
            if (state != null && state != before) return true
        }
        return false
    }

    private suspend fun awaitToggleScreen(timeoutMs: Long): Boolean {
        val calibration = prefs.calibration()
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            if (findToggle(calibration) != null) return true
            delay(300)
        }
        return false
    }

    companion object {
        private const val TAG = "HSAuto"
        private const val STATE_TIMEOUT_MS = 3_000L
        private const val SCREEN_WAIT_MS = 6_000L
        private const val MANUAL_WAIT_MS = 60_000L
        private const val TOTAL_TIMEOUT_MS = 75_000L
    }
}
