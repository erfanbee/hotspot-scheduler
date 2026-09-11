package com.iranjan.hotspotscheduler.accessibility

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.provider.Settings
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class HotspotNavigator @Inject constructor(@ApplicationContext private val context: Context) {

    private val candidates = listOf(
        ComponentName("com.android.settings", "com.android.settings.TetherSettings"),
        ComponentName("com.android.settings", "com.samsung.android.settings.wifi.mobileap.WifiApSettings"),
        ComponentName("com.android.settings", "com.android.settings.Settings\$TetherSettingsActivity"),
        ComponentName("com.samsung.android.settings", "com.samsung.android.settings.TetherSettings")
    )

    fun bestSettingsIntent(): Intent {
        val pm = context.packageManager
        for (cn in candidates) {
            val intent = Intent(Intent.ACTION_MAIN).setComponent(cn)
            try {
                if (pm.resolveActivity(intent, 0) != null) {
                    return intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                }
            } catch (t: Throwable) {
            }
        }
        return Intent(Settings.ACTION_WIRELESS_SETTINGS)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
    }

    fun launchHotspotSettings(): Boolean = try {
        context.startActivity(bestSettingsIntent())
        true
    } catch (t: Throwable) {
        false
    }
}
