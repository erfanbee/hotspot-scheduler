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
    suspend fun setHotspotState(targetOn: Boolean, password: String? = null): ToggleResult
    suspend fun setMobileData(targetOn: Boolean): ToggleResult
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
            val match = NodeMatcher.findToggle(service.rootNode(), calibration, KEYWORD_HOTSPOT)
                ?: return@withContext null
            NodeMatcher.readState(match)
        }
    }

    override suspend fun setHotspotState(targetOn: Boolean, password: String?): ToggleResult {
        val result = withTimeoutOrNull(TOTAL_TIMEOUT_MS) {
            runToggle(KEYWORD_HOTSPOT, targetOn, useCalibration = true, password = password)
        } ?: ToggleResult.FAILED
        if (result == ToggleResult.FAILED) {
            Log.e(TAG, "hotspot toggle failed targetOn=$targetOn acc=${AccessibilityServiceHolder.service != null} overlay=${Settings.canDrawOverlays(context)}")
        } else {
            prefs.setLastKnownHotspotOn(targetOn)
        }
        return result
    }

    override suspend fun setMobileData(targetOn: Boolean): ToggleResult {
        val result = withTimeoutOrNull(TOTAL_TIMEOUT_MS) {
            runToggle(KEYWORD_MOBILE_DATA, targetOn, useCalibration = false, password = null)
        } ?: ToggleResult.FAILED
        Log.i(TAG, "mobile data toggle targetOn=$targetOn result=$result")
        return result
    }

    override suspend fun requestCalibrationDump() {
        val service = AccessibilityServiceHolder.service ?: return
        service.emitCalibrationDump()
    }

    private suspend fun runToggle(
        rowKeyword: String,
        targetOn: Boolean,
        useCalibration: Boolean,
        password: String?
    ): ToggleResult {
        attempt(rowKeyword, targetOn, useCalibration, password)?.let { return it }

        if (Settings.canDrawOverlays(context)) {
            val launched = if (rowKeyword == KEYWORD_HOTSPOT) {
                navigator.launchHotspotSettings()
            } else {
                navigator.launchDataUsageSettings()
            }
            if (!launched) {
                Log.e(TAG, "could not launch settings screen for $rowKeyword")
                return ToggleResult.FAILED
            }
            awaitScreen(rowKeyword, useCalibration, SCREEN_WAIT_MS)
            attempt(rowKeyword, targetOn, useCalibration, password)?.let { return it }
            return ToggleResult.FAILED
        }

        notifications.postOpenHotspotSettingsPrompt()
        val deadline = System.currentTimeMillis() + MANUAL_WAIT_MS
        while (System.currentTimeMillis() < deadline) {
            attempt(rowKeyword, targetOn, useCalibration, password)?.let { return it }
            delay(500)
        }
        return ToggleResult.FAILED
    }

    private suspend fun attempt(
        rowKeyword: String,
        targetOn: Boolean,
        useCalibration: Boolean,
        password: String?
    ): ToggleResult? {
        val calibration = if (useCalibration) prefs.calibration() else null
        var match = findToggle(rowKeyword, calibration) ?: return null

        if (password != null && targetOn && rowKeyword == KEYWORD_HOTSPOT) {
            applyPassword(password)
            match = findToggle(rowKeyword, calibration) ?: return null
        }

        val before = withContext(Dispatchers.Main) { NodeMatcher.readState(match) } ?: return null
        if (before == targetOn) return ToggleResult.ALREADY_OK

        withContext(Dispatchers.Main) {
            match.clickTarget.performAction(AccessibilityNodeInfo.ACTION_CLICK)
        }
        if (awaitStateChange(before, rowKeyword, calibration)) return ToggleResult.TOGGLED

        val retryMatch = findToggle(rowKeyword, calibration)
        if (retryMatch != null) {
            val stateAfter = withContext(Dispatchers.Main) { NodeMatcher.readState(retryMatch) }
            if (stateAfter == targetOn) return ToggleResult.TOGGLED
            if (stateAfter == before) {
                withContext(Dispatchers.Main) {
                    retryMatch.clickTarget.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                }
                if (awaitStateChange(before, rowKeyword, calibration)) return ToggleResult.TOGGLED
            }
        }
        return ToggleResult.FAILED
    }

    private suspend fun applyPassword(password: String) {
        var editor = withContext(Dispatchers.Main) {
            NodeMatcher.findPasswordEditor(rootNode())
        }
        if (editor == null) {
            Log.i(TAG, "password field not on screen; opening hotspot config screen")
            openHotspotConfigScreen()
            editor = withContext(Dispatchers.Main) {
                NodeMatcher.findPasswordEditor(rootNode())
            }
        }
        if (editor == null) {
            Log.w(TAG, "password field not found; keeping the hotspot's own password")
            return
        }
        val applied = withContext(Dispatchers.Main) { NodeMatcher.setText(editor, password) }
        Log.i(TAG, "password applied=$applied")
        delay(400)
    }

    private suspend fun openHotspotConfigScreen() {
        val row = withContext(Dispatchers.Main) {
            NodeMatcher.findClickableRow(rootNode(), KEYWORD_HOTSPOT)
        } ?: run {
            Log.w(TAG, "hotspot row not found for click-through")
            return
        }
        withContext(Dispatchers.Main) {
            row.performAction(AccessibilityNodeInfo.ACTION_CLICK)
        }
        awaitScreen(KEYWORD_HOTSPOT, useCalibration = false, timeoutMs = 5_000L)
    }

    private suspend fun findToggle(
        rowKeyword: String,
        calibration: com.iranjan.hotspotscheduler.data.model.CalibrationSignature?
    ): ToggleMatch? {
        val service = AccessibilityServiceHolder.service ?: return null
        return withContext(Dispatchers.Main) {
            NodeMatcher.findToggle(service.rootNode(), calibration, rowKeyword)
        }
    }

    private suspend fun rootNode(): android.view.accessibility.AccessibilityNodeInfo? =
        AccessibilityServiceHolder.service?.rootNode()

    private suspend fun awaitStateChange(
        before: Boolean,
        rowKeyword: String,
        calibration: com.iranjan.hotspotscheduler.data.model.CalibrationSignature?
    ): Boolean {
        val deadline = System.currentTimeMillis() + STATE_TIMEOUT_MS
        while (System.currentTimeMillis() < deadline) {
            delay(300)
            val match = findToggle(rowKeyword, calibration) ?: continue
            val state = withContext(Dispatchers.Main) { NodeMatcher.readState(match) }
            if (state != null && state != before) return true
        }
        return false
    }

    private suspend fun awaitScreen(rowKeyword: String, useCalibration: Boolean, timeoutMs: Long): Boolean {
        val calibration = if (useCalibration) prefs.calibration() else null
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            if (findToggle(rowKeyword, calibration) != null) return true
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
