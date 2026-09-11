package com.iranjan.hotspotscheduler.service

import android.content.Intent
import android.util.Log
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import androidx.core.app.ServiceCompat
import android.content.pm.ServiceInfo
import com.iranjan.hotspotscheduler.accessibility.HotspotController
import com.iranjan.hotspotscheduler.accessibility.ToggleResult
import com.iranjan.hotspotscheduler.core.AlarmScheduler
import com.iranjan.hotspotscheduler.core.RoutineEvaluator
import com.iranjan.hotspotscheduler.data.prefs.AutomationPrefs
import com.iranjan.hotspotscheduler.data.repo.RoutineRepository
import com.iranjan.hotspotscheduler.data.usage.UsageMonitor
import com.iranjan.hotspotscheduler.data.usage.UsageSample
import com.iranjan.hotspotscheduler.util.AccessibilityUtils
import com.iranjan.hotspotscheduler.util.Formatters
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.time.ZoneId
import javax.inject.Inject

@AndroidEntryPoint
class HotspotAutomationService : LifecycleService() {

    @Inject lateinit var repo: RoutineRepository
    @Inject lateinit var prefs: AutomationPrefs
    @Inject lateinit var usageMonitor: UsageMonitor
    @Inject lateinit var controller: HotspotController
    @Inject lateinit var notifications: NotificationHelper
    @Inject lateinit var alarmScheduler: AlarmScheduler

    private val wake = Channel<Unit>(Channel.CONFLATED)

    override fun onCreate() {
        super.onCreate()
        ServiceCompat.startForeground(
            this,
            NotificationHelper.ID_STATUS,
            notifications.buildStatusNotification(initialStatus()),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
        )
        lifecycleScope.launch { loop() }
        Log.i(TAG, "foreground service created")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        when (intent?.action) {
            ACTION_PAUSE_TODAY -> lifecycleScope.launch { pauseToday() }
            ACTION_TURN_OFF_NOW -> lifecycleScope.launch { turnOffNow() }
            else -> wake.trySend(Unit)
        }
        return START_STICKY
    }

    private fun initialStatus() = StatusData(
        masterEnabled = true,
        paused = false,
        suppressed = false,
        capHitToday = false,
        hotspotOn = null,
        activeRoutineName = null,
        usageBytes = null,
        capMb = null,
        accessibilityOk = true
    )

    private suspend fun loop() {
        while (true) {
            try {
                tick()
            } catch (t: Throwable) {
                Log.e(TAG, "tick failed", t)
            }
            withTimeoutOrNull(TICK_MS) { wake.receive() }
        }
    }

    private suspend fun tick() {
        val now = System.currentTimeMillis()
        val zone = ZoneId.systemDefault()
        val accOk = AccessibilityUtils.isServiceEnabled(applicationContext)
        if (!accOk) {
            val lastAlert = prefs.lastAccAlertMs.first()
            if (now - lastAlert > ACC_ALERT_COOLDOWN_MS) {
                notifications.notifyAccessibilityDisabled()
                prefs.setLastAccAlertMs(now)
            }
        }

        val master = prefs.masterEnabled.first()
        val paused = prefs.pausedUntilMs.first() > now
        val today = RoutineEvaluator.todayEpochDay(now, zone)
        val capHit = prefs.capHitEpochDay.first() == today
        val routines = repo.enabledRoutines()
        val active = RoutineEvaluator.activeRoutines(routines, now, zone)
        val capMb = RoutineEvaluator.strictestCapMb(active)
        val usage = usageMonitor.hotspotBytesSinceMidnight()
        usage?.let { repo.upsertUsageDay(today, it.bytes) }
        repo.pruneUsage(today - KEEP_DAYS)

        if (master) {
            val last = RoutineEvaluator.lastBoundary(routines, now, zone)
            val lastApplied = prefs.lastAppliedBoundary.first()
            if (last != null && lastApplied != last.key) {
                applyBoundary(last, paused, capHit, capMb, usage)
                alarmScheduler.rescheduleAll()
            }
        }

        if (master && !paused && !capHit && capMb != null) {
            val bytes = usage?.bytes ?: 0L
            if (bytes >= capMb * 1024L * 1024L) {
                prefs.setCapHitEpochDay(today)
                val result = controller.setHotspotState(false)
                notifications.notifyCapReached(
                    Formatters.formatBytes(bytes),
                    Formatters.formatCapMb(capMb),
                    result == ToggleResult.FAILED
                )
                Log.i(TAG, "cap reached usage=$bytes capMb=$capMb result=$result")
            }
        }

        val suppressed = prefs.suppressedUntilNextWindow.first()
        val freshCapHit = prefs.capHitEpochDay.first() == today
        val hotspotKnown = controller.readHotspotState() ?: prefs.lastKnownHotspotOn.first()
        notifications.notifyStatus(
            StatusData(
                masterEnabled = master,
                paused = paused,
                suppressed = suppressed,
                capHitToday = freshCapHit,
                hotspotOn = hotspotKnown,
                activeRoutineName = active.firstOrNull()?.name,
                usageBytes = usage?.bytes,
                capMb = capMb,
                accessibilityOk = accOk
            )
        )
        WidgetProvider.updateAll(applicationContext, hotspotKnown, usage?.bytes, capMb)
    }

    private suspend fun applyBoundary(
        boundary: RoutineEvaluator.Boundary,
        paused: Boolean,
        capHit: Boolean,
        capMb: Long?,
        usage: UsageSample?
    ) {
        val usageBytes = usage?.bytes ?: 0L
        if (boundary.isStart) {
            prefs.setSuppressedUntilNextWindow(false)
            val freshCapHit = prefs.capHitEpochDay.first() == RoutineEvaluator.todayEpochDay(System.currentTimeMillis())
            if (!paused && !freshCapHit && (capMb == null || usageBytes < capMb * 1024L * 1024L)) {
                val result = controller.setHotspotState(true)
                if (result == ToggleResult.FAILED) notifications.notifyToggleFailed()
                Log.i(TAG, "boundary START applied result=$result")
            } else {
                Log.i(TAG, "boundary START skipped paused=$paused capHit=$freshCapHit capMb=$capMb usage=$usageBytes")
            }
        } else {
            val result = controller.setHotspotState(false)
            if (result == ToggleResult.FAILED) notifications.notifyToggleFailed()
            Log.i(TAG, "boundary END applied result=$result")
        }
        prefs.setLastAppliedBoundary(boundary.key)
    }

    private suspend fun pauseToday() {
        val midnight = RoutineEvaluator.nextMidnight(System.currentTimeMillis())
        prefs.setPausedUntilMs(midnight)
        Log.i(TAG, "paused until $midnight")
        wake.trySend(Unit)
    }

    private suspend fun turnOffNow() {
        val result = controller.setHotspotState(false)
        val now = System.currentTimeMillis()
        val active = RoutineEvaluator.activeRoutines(repo.enabledRoutines(), now)
        if (active.isNotEmpty()) {
            prefs.setSuppressedUntilNextWindow(true)
        }
        if (result == ToggleResult.FAILED) {
            notifications.notifyToggleFailed()
        }
        Log.i(TAG, "turnOffNow result=$result suppressed=${active.isNotEmpty()}")
        wake.trySend(Unit)
    }

    companion object {
        private const val TAG = "HSAuto"
        private const val TICK_MS = 2 * 60 * 1000L
        private const val ACC_ALERT_COOLDOWN_MS = 60 * 60 * 1000L
        private const val KEEP_DAYS = 30L

        const val ACTION_START = "com.iranjan.hotspotscheduler.START"
        const val ACTION_REFRESH = "com.iranjan.hotspotscheduler.REFRESH"
        const val ACTION_BOUNDARY = AlarmScheduler.ACTION_BOUNDARY
        const val ACTION_MIDNIGHT = AlarmScheduler.ACTION_MIDNIGHT
        const val ACTION_PAUSE_TODAY = "com.iranjan.hotspotscheduler.PAUSE_TODAY"
        const val ACTION_TURN_OFF_NOW = "com.iranjan.hotspotscheduler.TURN_OFF_NOW"

        fun start(context: android.content.Context, action: String, base: Intent? = null) {
            val intent = Intent(context, HotspotAutomationService::class.java).setAction(action)
            base?.let { intent.putExtras(it) }
            context.startForegroundService(intent)
        }
    }
}
