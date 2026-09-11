package com.iranjan.hotspotscheduler.accessibility

import android.app.KeyguardManager
import android.content.Context
import android.os.PowerManager
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

    private val powerManager: PowerManager? = context.getSystemService(PowerManager::class.java)
    private val keyguardManager: KeyguardManager? = context.getSystemService(KeyguardManager::class.java)
    private var keyguardLock: KeyguardManager.KeyguardLock? = null

    override suspend fun readHotspotState(): Boolean? {
        val service = AccessibilityServiceHolder.service ?: return null
        val calibration = prefs.calibration()
        return withContext(Dispatchers.Main) {
            val root = service.rootNode()
            if (root == null || root.packageName?.toString() != SETTINGS_PACKAGE) return@withContext null
            val match = NodeMatcher.findToggle(root, calibration, KEYWORD_HOTSPOT) ?: return@withContext null
            NodeMatcher.readState(match)
        }
    }

    override suspend fun setHotspotState(targetOn: Boolean, password: String?): ToggleResult {
        val result = withTimeoutOrNull(TOTAL_TIMEOUT_MS) {
            runToggle(KEYWORD_HOTSPOT, targetOn, useCalibration = true, password = password)
        } ?: ToggleResult.FAILED
        AttemptLog.add("HOTSPOT target=$targetOn -> $result")
        if (result == ToggleResult.FAILED) {
            Log.e(TAG, "hotspot toggle failed targetOn=$targetOn")
        } else {
            prefs.setLastKnownHotspotOn(targetOn)
        }
        return result
    }

    override suspend fun setMobileData(targetOn: Boolean): ToggleResult {
        val result = withTimeoutOrNull(TOTAL_TIMEOUT_MS) {
            runToggle(KEYWORD_MOBILE_DATA, targetOn, useCalibration = false, password = null)
        } ?: ToggleResult.FAILED
        AttemptLog.add("MOBILE DATA target=$targetOn -> $result")
        Log.i(TAG, "mobile data toggle targetOn=$targetOn result=$result")
        return result
    }

    override suspend fun requestCalibrationDump() {
        val service = AccessibilityServiceHolder.service ?: return
        service.emitCalibrationDump()
    }

    private fun isUnlocked(): Boolean {
        val pm = powerManager ?: return true
        if (!pm.isInteractive) return false
        val km = keyguardManager ?: return true
        return !km.isKeyguardLocked
    }

    private fun wakeAndDisableKeyguard() {
        val pm = powerManager ?: return
        if (!pm.isInteractive) {
            AttemptLog.add("screen off; waking")
            try {
                val wl = pm.newWakeLock(
                    PowerManager.SCREEN_BRIGHT_WAKE_LOCK or PowerManager.ACQUIRE_CAUSES_WAKEUP,
                    "HSAuto:wake"
                )
                wl.acquire(30_000L)
            } catch (t: Throwable) {
                AttemptLog.add("wake failed: ${t.message}")
            }
        }
        val km = keyguardManager ?: return
        if (km.isKeyguardLocked) {
            val secure = try {
                km.isKeyguardSecure
            } catch (t: Throwable) {
                false
            }
            AttemptLog.add("keyguard locked (secure=$secure); disabling non-secure lock")
            try {
                keyguardLock?.reenableKeyguard()
                keyguardLock = km.newKeyguardLock("HSAuto").also { it.disableKeyguard() }
            } catch (t: Throwable) {
                AttemptLog.add("keyguard disable failed: ${t.message}")
            }
        }
    }

    private fun restoreKeyguard() {
        try {
            keyguardLock?.reenableKeyguard()
        } catch (t: Throwable) {
        }
        keyguardLock = null
    }

    private suspend fun runToggle(
        rowKeyword: String,
        targetOn: Boolean,
        useCalibration: Boolean,
        password: String?
    ): ToggleResult {
        try {
            return runToggleInner(rowKeyword, targetOn, useCalibration, password)
        } finally {
            restoreKeyguard()
        }
    }

    private suspend fun runToggleInner(
        rowKeyword: String,
        targetOn: Boolean,
        useCalibration: Boolean,
        password: String?
    ): ToggleResult {
        AttemptLog.add("=== toggle $rowKeyword target=$targetOn start")
        attempt(rowKeyword, targetOn, useCalibration, password)?.let { return it }

        wakeAndDisableKeyguard()
        delay(1_500)
        attempt(rowKeyword, targetOn, useCalibration, password)?.let { return it }

        var launched = false
        if (isUnlocked() && Settings.canDrawOverlays(context)) {
            launched = launchFor(rowKeyword)
            awaitScreen(rowKeyword, useCalibration, SCREEN_WAIT_MS)
            attempt(rowKeyword, targetOn, useCalibration, password)?.let { return it }
            navigateFor(rowKeyword)
            attempt(rowKeyword, targetOn, useCalibration, password)?.let { return it }
            logScreenDump(rowKeyword)
            return ToggleResult.FAILED
        }

        if (!isUnlocked()) {
            AttemptLog.add("device locked; waiting up to ${MANUAL_WAIT_MS / 1000}s for unlock (secure PIN cannot be bypassed)")
        }
        notifications.postOpenHotspotSettingsPrompt()
        val deadline = System.currentTimeMillis() + MANUAL_WAIT_MS
        var iterations = 0
        while (System.currentTimeMillis() < deadline) {
            if (!launched && isUnlocked() && Settings.canDrawOverlays(context)) {
                launched = launchFor(rowKeyword)
            }
            attempt(rowKeyword, targetOn, useCalibration, password)?.let { return it }
            iterations++
            if (iterations % 8 == 0) {
                navigateFor(rowKeyword)
            }
            delay(500)
        }
        logScreenDump(rowKeyword)
        return ToggleResult.FAILED
    }

    private suspend fun navigateFor(rowKeyword: String): Boolean {
        val intermediates = if (rowKeyword == KEYWORD_MOBILE_DATA) {
            listOf("data usage", "connections")
        } else {
            listOf("mobile hotspot and tethering", "connections")
        }
        for (label in intermediates) {
            val row = withContext(Dispatchers.Main) {
                NodeMatcher.findClickableRow(rootNode(), label)
            } ?: continue
            withContext(Dispatchers.Main) {
                row.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            }
            AttemptLog.add("clicked '$label' to navigate")
            delay(1_800)
            if (findToggle(rowKeyword, null) != null) return true
        }
        return false
    }

    private suspend fun logScreenDump(rowKeyword: String) {
        val root = rootNode() ?: return
        val lines = withContext(Dispatchers.Main) { NodeDumper.dumpCompact(root, 40) }
        AttemptLog.add("screen dump for '$rowKeyword':")
        lines.forEach { AttemptLog.add(it) }
    }

    private suspend fun launchFor(rowKeyword: String): Boolean {
        val launched = if (rowKeyword == KEYWORD_HOTSPOT) {
            navigator.launchHotspotSettings()
        } else {
            navigator.launchDataUsageSettings()
        }
        val root = rootNode()
        AttemptLog.add("launched=${launched} screen=${root?.className} pkg=${root?.packageName}")
        return launched
    }

    private suspend fun attempt(
        rowKeyword: String,
        targetOn: Boolean,
        useCalibration: Boolean,
        password: String?
    ): ToggleResult? {
        val calibration = if (useCalibration) prefs.calibration() else null
        var match = findToggle(rowKeyword, calibration)
        if (match == null) {
            if (rowKeyword == KEYWORD_HOTSPOT && password != null && targetOn) {
                openHotspotConfigScreen()
                match = findToggle(rowKeyword, calibration) ?: return null
            } else {
                return null
            }
        }
        AttemptLog.add("match=${match.source}")

        if (password != null && targetOn && rowKeyword == KEYWORD_HOTSPOT) {
            applyPassword(password)
            match = findToggle(rowKeyword, calibration) ?: return null
        }

        val before = withContext(Dispatchers.Main) { NodeMatcher.readState(match) }
        if (before == null) {
            AttemptLog.add("state unreadable")
            return null
        }
        AttemptLog.add("state before=$before")
        if (before == targetOn) return ToggleResult.ALREADY_OK

        withContext(Dispatchers.Main) {
            match.clickTarget.performAction(AccessibilityNodeInfo.ACTION_CLICK)
        }
        AttemptLog.add("click sent")
        if (awaitStateChange(before, rowKeyword, calibration)) {
            AttemptLog.add("state changed OK")
            return ToggleResult.TOGGLED
        }

        val retryMatch = findToggle(rowKeyword, calibration)
        if (retryMatch != null) {
            val stateAfter = withContext(Dispatchers.Main) { NodeMatcher.readState(retryMatch) }
            if (stateAfter == targetOn) return ToggleResult.TOGGLED
            if (stateAfter == before) {
                AttemptLog.add("retrying click once")
                withContext(Dispatchers.Main) {
                    retryMatch.clickTarget.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                }
                if (awaitStateChange(before, rowKeyword, calibration)) {
                    AttemptLog.add("state changed OK after retry")
                    return ToggleResult.TOGGLED
                }
            }
        }
        return ToggleResult.FAILED
    }

    private suspend fun applyPassword(password: String) {
        var editor = withContext(Dispatchers.Main) {
            NodeMatcher.findPasswordEditor(settingsRoot())
        }
        if (editor == null) {
            Log.i(TAG, "password field not on screen; opening hotspot config screen")
            openHotspotConfigScreen()
            editor = withContext(Dispatchers.Main) {
                NodeMatcher.findPasswordEditor(settingsRoot())
            }
        }
        if (editor == null) {
            AttemptLog.add("password field not found; keeping existing password")
            return
        }
        val applied = withContext(Dispatchers.Main) { NodeMatcher.setText(editor, password) }
        AttemptLog.add("password applied=$applied")
        delay(400)
    }

    private suspend fun openHotspotConfigScreen() {
        val row = withContext(Dispatchers.Main) {
            NodeMatcher.findClickableRow(settingsRoot(), KEYWORD_HOTSPOT)
        } ?: run {
            AttemptLog.add("hotspot row not found for click-through")
            return
        }
        withContext(Dispatchers.Main) {
            row.performAction(AccessibilityNodeInfo.ACTION_CLICK)
        }
        AttemptLog.add("clicked hotspot row to open config screen")
        val opened = awaitPasswordField(6_000L)
        AttemptLog.add("config screen with password field=$opened")
    }

    private suspend fun findToggle(
        rowKeyword: String,
        calibration: com.iranjan.hotspotscheduler.data.model.CalibrationSignature?
    ): ToggleMatch? {
        val service = AccessibilityServiceHolder.service ?: run {
            AttemptLog.add("accessibility service not connected")
            return null
        }
        return withContext(Dispatchers.Main) {
            val root = service.rootNode()
            when {
                root == null -> null
                root.packageName?.toString() != SETTINGS_PACKAGE -> {
                    AttemptLog.add("ignoring own window; waiting for settings")
                    null
                }
                else -> NodeMatcher.findToggle(root, calibration, rowKeyword)
            }
        }
    }

    private suspend fun settingsRoot(): AccessibilityNodeInfo? = withContext(Dispatchers.Main) {
        val root = rootNode() ?: return@withContext null
        if (root.packageName?.toString() != SETTINGS_PACKAGE) null else root
    }

    private suspend fun awaitPasswordField(timeoutMs: Long): Boolean {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            val root = settingsRoot()
            if (root != null && NodeMatcher.findPasswordEditor(root) != null) return true
            delay(300)
        }
        return false
    }

    private suspend fun rootNode(): AccessibilityNodeInfo? =
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
        const val SETTINGS_PACKAGE = "com.android.settings"
        private const val TAG = "HSAuto"
        private const val STATE_TIMEOUT_MS = 3_000L
        private const val SCREEN_WAIT_MS = 6_000L
        private const val MANUAL_WAIT_MS = 180_000L
        private const val TOTAL_TIMEOUT_MS = 200_000L
    }
}
