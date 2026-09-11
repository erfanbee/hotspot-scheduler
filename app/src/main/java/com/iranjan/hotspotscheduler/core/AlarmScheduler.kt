package com.iranjan.hotspotscheduler.core

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.iranjan.hotspotscheduler.data.prefs.AutomationPrefs
import com.iranjan.hotspotscheduler.data.repo.RoutineRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AlarmScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repo: RoutineRepository,
    private val prefs: AutomationPrefs
) {
    suspend fun rescheduleAll() {
        val alarmManager = context.getSystemService(AlarmManager::class.java) ?: return
        val now = System.currentTimeMillis()
        val zone = ZoneId.systemDefault()
        val master = prefs.masterEnabled.first()
        val next = if (master) {
            RoutineEvaluator.nextBoundary(repo.enabledRoutines(), now, zone)
        } else {
            null
        }
        schedule(alarmManager, RC_MIDNIGHT, RoutineEvaluator.nextMidnight(now, zone), ACTION_MIDNIGHT, null)
        if (next != null) {
            schedule(alarmManager, requestCodeFor(next), next.atMillis, ACTION_BOUNDARY, next)
        }
    }

    suspend fun cancelAll() {
        val alarmManager = context.getSystemService(AlarmManager::class.java) ?: return
        alarmManager.cancel(pendingIntent(RC_MIDNIGHT, ACTION_MIDNIGHT))
        for (routine in repo.enabledRoutines()) {
            alarmManager.cancel(pendingIntent(requestCodeFor(RoutineEvaluator.Boundary(routine.id, true, 0)), ACTION_BOUNDARY))
            alarmManager.cancel(pendingIntent(requestCodeFor(RoutineEvaluator.Boundary(routine.id, false, 0)), ACTION_BOUNDARY))
        }
    }

    private fun requestCodeFor(boundary: RoutineEvaluator.Boundary): Int =
        if (boundary.isStart) RC_BOUNDARY_START + boundary.routineId.toInt()
        else RC_BOUNDARY_END + boundary.routineId.toInt()

    private fun pendingIntent(requestCode: Int, action: String, boundary: RoutineEvaluator.Boundary? = null): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            requestCode,
            buildIntent(action, boundary),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

    private fun buildIntent(action: String, boundary: RoutineEvaluator.Boundary?): Intent =
        Intent(context, AlarmReceiver::class.java).setAction(action).apply {
            boundary?.let {
                putExtra(EXTRA_ROUTINE_ID, it.routineId)
                putExtra(EXTRA_IS_START, it.isStart)
                putExtra(EXTRA_AT_MILLIS, it.atMillis)
            }
        }

    private fun schedule(
        alarmManager: AlarmManager,
        requestCode: Int,
        atMillis: Long,
        action: String,
        boundary: RoutineEvaluator.Boundary?
    ) {
        val pi = pendingIntent(requestCode, action, boundary)
        val exactAllowed = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarmManager.canScheduleExactAlarms()
        if (exactAllowed) {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atMillis, pi)
        } else {
            alarmManager.setWindow(AlarmManager.RTC_WAKEUP, atMillis, 60_000L, pi)
        }
    }

    companion object {
        const val ACTION_BOUNDARY = "com.iranjan.hotspotscheduler.ACTION_BOUNDARY"
        const val ACTION_MIDNIGHT = "com.iranjan.hotspotscheduler.ACTION_MIDNIGHT"
        const val EXTRA_ROUTINE_ID = "routine_id"
        const val EXTRA_IS_START = "is_start"
        const val EXTRA_AT_MILLIS = "at_millis"
        private const val RC_MIDNIGHT = 7
        private const val RC_BOUNDARY_START = 1_000_000
        private const val RC_BOUNDARY_END = 2_000_000
    }
}
