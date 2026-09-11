package com.iranjan.hotspotscheduler.service

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.iranjan.hotspotscheduler.R
import com.iranjan.hotspotscheduler.data.repo.RoutineRepository
import com.iranjan.hotspotscheduler.util.Formatters
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class WidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val entry = EntryPointAccessors.fromApplication(context, WidgetEntryPoint::class.java)
                val latest = entry.repo().latestUsage()
                val hotspotOn = entry.prefs().lastKnownHotspotOn.firstOrNull()
                withContext(Dispatchers.Main) {
                    updateAll(context, hotspotOn, latest?.bytes, null)
                }
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        fun updateAll(context: Context, hotspotOn: Boolean?, usageBytes: Long?, capMb: Long?) {
            val stateText = when (hotspotOn) {
                true -> context.getString(R.string.widget_on)
                false -> context.getString(R.string.widget_off)
                null -> context.getString(R.string.widget_unknown)
            }
            val usageText = if (usageBytes != null) {
                val used = Formatters.formatBytes(usageBytes)
                if (capMb != null) "$used / ${Formatters.formatCapMb(capMb)}" else used
            } else {
                ""
            }
            val views = buildRemoteViews(context, stateText, usageText)
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, WidgetProvider::class.java))
            if (ids.isNotEmpty()) {
                manager.updateAppWidget(ids, views)
            }
        }

        private fun buildRemoteViews(context: Context, stateText: String, usageText: String): RemoteViews {
            val views = RemoteViews(context.packageName, R.layout.widget_layout)
            views.setTextViewText(R.id.widget_state, stateText)
            views.setTextViewText(R.id.widget_usage, usageText)
            views.setOnClickPendingIntent(
                R.id.widget_pause,
                PendingIntent.getBroadcast(
                    context,
                    31,
                    Intent(context, ActionReceiver::class.java).setAction(HotspotAutomationService.ACTION_PAUSE_TODAY),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
            )
            views.setOnClickPendingIntent(
                R.id.widget_turn_off,
                PendingIntent.getBroadcast(
                    context,
                    32,
                    Intent(context, ActionReceiver::class.java).setAction(HotspotAutomationService.ACTION_TURN_OFF_NOW),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
            )
            views.setOnClickPendingIntent(
                R.id.widget_root,
                PendingIntent.getActivity(
                    context,
                    33,
                    context.packageManager.getLaunchIntentForPackage(context.packageName),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
            )
            return views
        }
    }
}

@EntryPoint
@InstallIn(SingletonComponent::class)
interface WidgetEntryPoint {
    fun repo(): RoutineRepository
    fun prefs(): com.iranjan.hotspotscheduler.data.prefs.AutomationPrefs
}
