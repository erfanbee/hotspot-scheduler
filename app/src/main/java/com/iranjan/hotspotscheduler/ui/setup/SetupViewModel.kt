package com.iranjan.hotspotscheduler.ui.setup

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
import com.iranjan.hotspotscheduler.util.AccessibilityUtils
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import android.Manifest
import javax.inject.Inject

data class SetupState(
    val accessibility: Boolean = false,
    val usageAccess: Boolean = false,
    val notifications: Boolean = false,
    val exactAlarms: Boolean = false,
    val battery: Boolean = false,
    val overlay: Boolean = false
)

@HiltViewModel
class SetupViewModel @Inject constructor(
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val _state = MutableStateFlow(SetupState())
    val state: StateFlow<SetupState> = _state

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
    }

    private fun hasUsageAccess(): Boolean = try {
        val appOps = context.getSystemService(AppOpsManager::class.java) ?: return false
        appOps.checkOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS,
            Process.myUid(),
            context.packageName
        ) == AppOpsManager.MODE_ALLOWED
    } catch (t: Throwable) {
        false
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
