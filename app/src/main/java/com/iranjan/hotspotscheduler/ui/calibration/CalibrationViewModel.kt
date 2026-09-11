package com.iranjan.hotspotscheduler.ui.calibration

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.iranjan.hotspotscheduler.accessibility.AccessibilityServiceHolder
import com.iranjan.hotspotscheduler.accessibility.HotspotController
import com.iranjan.hotspotscheduler.accessibility.HotspotNavigator
import com.iranjan.hotspotscheduler.data.model.CalibrationSignature
import com.iranjan.hotspotscheduler.data.model.NodeDump
import com.iranjan.hotspotscheduler.data.prefs.AutomationPrefs
import com.iranjan.hotspotscheduler.service.NotificationHelper
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class CalibrationViewModel @Inject constructor(
    private val prefs: AutomationPrefs,
    private val controller: HotspotController,
    private val navigator: HotspotNavigator,
    private val notifications: NotificationHelper
) : ViewModel() {

    val dumps = AccessibilityServiceHolder.calibrationDumps

    private val _mode = MutableStateFlow(AccessibilityServiceHolder.calibrationMode)
    val mode: StateFlow<Boolean> = _mode

    private val _saved = MutableStateFlow<CalibrationSignature?>(null)
    val saved: StateFlow<CalibrationSignature?> = _saved

    init {
        viewModelScope.launch {
            _saved.value = prefs.calibration()
        }
    }

    fun start() {
        AccessibilityServiceHolder.calibrationMode = true
        AccessibilityServiceHolder.clearDumps()
        _mode.value = true
        if (!navigator.launchHotspotSettings()) {
            notifications.postOpenHotspotSettingsPrompt()
        }
    }

    fun stop() {
        AccessibilityServiceHolder.calibrationMode = false
        _mode.value = false
    }

    fun rescan() = viewModelScope.launch {
        controller.requestCalibrationDump()
    }

    fun persist(dump: NodeDump) = viewModelScope.launch {
        prefs.setCalibration(dump.signature())
        _saved.value = prefs.calibration()
        stop()
    }

    fun clear() = viewModelScope.launch {
        prefs.clearCalibration()
        _saved.value = null
    }
}
