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

    private val hotspotCandidates = listOf(
        ComponentName("com.android.settings", "com.samsung.android.settings.wifi.mobileap.WifiApSettings"),
        ComponentName("com.samsung.android.settings", "com.samsung.android.settings.wifi.mobileap.WifiApSettings"),
        ComponentName("com.android.settings", "com.android.settings.wifi.tether.WifiTetherSettings"),
        ComponentName("com.android.settings", "com.android.settings.TetherSettings"),
        ComponentName("com.android.settings", "com.android.settings.Settings\$TetherSettingsActivity"),
        ComponentName("com.samsung.android.settings", "com.samsung.android.settings.TetherSettings")
    )

    private val dataUsageCandidates = listOf(
        ComponentName("com.android.settings", "com.android.settings.Settings\$DataUsageSummaryActivity"),
        ComponentName("com.android.settings", "com.android.settings.datausage.DataUsageSettings"),
        ComponentName("com.samsung.android.settings", "com.samsung.android.settings.datausage.DataUsageSettings"),
        ComponentName("com.android.settings", "com.samsung.android.settings.datausage.DataUsageSettings")
    )

    private fun resolvableIntents(candidates: List<ComponentName>): List<Intent> {
        val pm = context.packageManager
        val result = mutableListOf<Intent>()
        for (cn in candidates) {
            val intent = Intent(Intent.ACTION_MAIN).setComponent(cn)
            try {
                if (pm.resolveActivity(intent, 0) != null) {
                    result.add(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP))
                }
            } catch (t: Throwable) {
            }
        }
        return result
    }

    private fun launchFirst(intents: List<Intent>, fallback: Intent): Boolean = try {
        val intent = intents.firstOrNull() ?: fallback
        context.startActivity(intent)
        true
    } catch (t: Throwable) {
        false
    }

    fun hotspotSettingsIntents(): List<Intent> =
        resolvableIntents(hotspotCandidates) + listOf(
            Intent(Settings.ACTION_WIRELESS_SETTINGS)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        )

    fun dataUsageIntents(): List<Intent> =
        resolvableIntents(dataUsageCandidates) + listOf(
            Intent(Settings.ACTION_DATA_USAGE_SETTINGS)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        )

    fun launchHotspotSettings(): Boolean = try {
        context.startActivity(hotspotSettingsIntents().first())
        true
    } catch (t: Throwable) {
        false
    }

    fun launchDataUsageSettings(): Boolean = try {
        context.startActivity(dataUsageIntents().first())
        true
    } catch (t: Throwable) {
        false
    }

    fun bestSettingsIntent(): Intent = hotspotSettingsIntents().first()

    fun bestDataUsageIntent(): Intent = dataUsageIntents().first()
}
