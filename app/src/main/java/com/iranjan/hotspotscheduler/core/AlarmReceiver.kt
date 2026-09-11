package com.iranjan.hotspotscheduler.core

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.iranjan.hotspotscheduler.service.HotspotAutomationService

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        if (action != AlarmScheduler.ACTION_BOUNDARY && action != AlarmScheduler.ACTION_MIDNIGHT) return
        HotspotAutomationService.start(context, action, intent)
    }
}
