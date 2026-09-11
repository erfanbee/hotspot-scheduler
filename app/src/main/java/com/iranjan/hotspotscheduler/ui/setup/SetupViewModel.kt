package com.iranjan.hotspotscheduler.ui.setup

import android.Manifest
import android.app.AlarmManager
import android.app.AppOpsManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.PowerManager
import android.os.Process
import android.provider.Settings
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.iranjan.hotspotscheduler.accessibility.AttemptLog
import com.iranjan.hotspotscheduler.accessibility.HotspotController
import com.iranjan.hotspotscheduler.data.repo.RoutineRepository
import com.iranjan.hotspotscheduler.toggle.ShizukuEngine
import com.iranjan.hotspotscheduler.util.AccessibilityUtils
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.rikka.shizuku.Shizuku
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SetupState(
    val accessibility: Boolean = false,
    val usageAccess: Boolean = false,
    val notifications: Boolean = false,
    val exactAlarms: Boolean = false,
    val battery: Boolean = false,
    val overlay: Boolean = false
)

data class ShizukuStatus(
    val installed: Boolean,
    val running: Boolean,
    val granted: Boolean
)

@HiltViewModel
class SetupViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val controller: HotspotController,
    private val repo: RoutineRepository,
    private val shizuku: ShizukuEngine
) : ViewModel() {

    private val _state = MutableStateFlow(SetupState())
    val state: StateFlow<SetupState> = _state

    private val _testRunning = MutableStateFlow(false)
    val testRunning: StateFlow<Boolean> = _testRunning

    private val _shizuku = MutableStateFlow(ShizukuStatus(false, false, false))
    val shizuku: StateFlow<ShizukuStatus> = _shizuku

    private val permissionListener =
        Shizuku.OnRequestPermissionResultListener { _, grantResult ->
            _shizuku.value = shizukuStatus()
            refresh()
        }

    init {
        try {
            Shizuku.addRequestPermissionResultListener(permissionListener)
        } catch (t: Throwable) {
        }
        refresh()
    }

    override fun onCleared() {
        try {
            Shizuku.removeRequestPermissionResultListener(permissionListener)
        } catch (t: Throwable) {
        }
    }

    private fun shizukuStatus(): ShizukuStatus = ShizukuStatus(
        installed = isShizukuInstalled(),
        running = shizuku.isRunning(),
        granted = shizuku.hasPermission()
    )

    private fun isShizukuInstalled(): Boolean = try {
        context.packageManager.getPackageInfo("moe.shizuku.privileged.api", 0) != null
    } catch (t: Throwable) {
        false
    }

    fun requestShizukuPermission() = try {
        Shizuku.requestPermission(1001)
    } catch (t: Throwable) {
        AttemptLog.add("shizuku permission request failed: ${t.message}")
    }

    fun refresh() {
        val alarmManager = context.getSystemService(AlarmManager::class.java)
        _state.value = SetupState(
            accessibility = AccessibilityUtils.isServiceEnabled(context),
            usageAccess = hasUsageAccess(),
            notifications = notificationGranted(),
            exactAlarms = Build.VERSION.SDK_INT < 31 || alarmManager?.canScheduleExactAlarms() == true,
            battery = isIgnoringBattery(),
            overlay = Settings.canDrawOverlays(context)
        )
        _shizuku.value = shizukuStatus()
    }

    fun testHotspot(on: Boolean) = viewModelScope.launch {
        _testRunning.value = true
        try {
            val password = repo.enabledRoutines()
                .firstOrNull { !it.hotspotPassword.isNullOrBlank() }?.hotspotPassword
            if (password != null) {
                AttemptLog.add("live test: using password from an enabled routine")
            }
            controller.setHotspotState(on, password)
        } finally {
            _testRunning.value = false
        }
    }

    fun testMobileData(on: Boolean) = viewModelScope.launch {
        _testRunning.value = true
        try {
            controller.setMobileData(on)
        } finally {
            _testRunning.value = false
        }
    }

    fun shareText(): String = AttemptLog.snapshot().joinToString("\n")

    private fun hasUsageAccess(): Boolean {
        return try {
            val appOps = context.getSystemService(AppOpsManager::class.java) ?: return false
            appOps.checkOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(),
                context.packageName
            ) == AppOpsManager.MODE_ALLOWED
        } catch (t: Throwable) {
            false
        }
    }

    private fun notificationGranted(): Boolean =
        Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED

    private fun isIgnoringBattery(): Boolean = try {
        context.getSystemService(PowerManager::class.java)
            ?.isIgnoringBatteryOptimizations(context.packageName) == true
    } catch (t: Throwable) {
        false
    }
}
