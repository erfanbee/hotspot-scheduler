package com.iranjan.hotspotscheduler.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class ActionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            HotspotAutomationService.ACTION_PAUSE_TODAY ->
                HotspotAutomationService.start(context, HotspotAutomationService.ACTION_PAUSE_TODAY)
            HotspotAutomationService.ACTION_TURN_OFF_NOW ->
                HotspotAutomationService.start(context, HotspotAutomationService.ACTION_TURN_OFF_NOW)
        }
    }
}
