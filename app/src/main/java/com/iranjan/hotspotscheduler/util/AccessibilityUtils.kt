package com.iranjan.hotspotscheduler.util

import android.content.ComponentName
import android.content.Context
import android.provider.Settings
import com.iranjan.hotspotscheduler.accessibility.HotspotAccessibilityService

object AccessibilityUtils {

    fun isServiceEnabled(context: Context): Boolean {
        val enabled = Settings.Secure.getInt(
            context.contentResolver,
            Settings.Secure.ACCESSIBILITY_ENABLED,
            0
        ) == 1
        if (!enabled) return false
        val expected = ComponentName(context, HotspotAccessibilityService::class.java).flattenToString()
        val services = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false
        return services.split(":").any { it.equals(expected, ignoreCase = true) }
    }
}
