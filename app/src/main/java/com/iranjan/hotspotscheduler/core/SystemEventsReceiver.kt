package com.iranjan.hotspotscheduler.core

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.iranjan.hotspotscheduler.service.HotspotAutomationService

class SystemEventsReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED,
            Intent.ACTION_MY_PACKAGE_REPLACED -> HotspotAutomationService.start(context, HotspotAutomationService.ACTION_REFRESH)
        }
    }
}
