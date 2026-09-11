package com.iranjan.hotspotscheduler.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.provider.Settings
import androidx.core.app.NotificationCompat
import com.iranjan.hotspotscheduler.R
import com.iranjan.hotspotscheduler.accessibility.HotspotNavigator
import com.iranjan.hotspotscheduler.ui.MainActivity
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

data class StatusData(
    val masterEnabled: Boolean,
    val paused: Boolean,
    val suppressed: Boolean,
    val capHitToday: Boolean,
    val hotspotOn: Boolean?,
    val activeRoutineName: String?,
    val usageBytes: Long?,
    val capMb: Long?,
    val accessibilityOk: Boolean
)

@Singleton
class NotificationHelper @Inject constructor(
    @ApplicationContext private val context: Context,
    private val navigator: HotspotNavigator
) {
    private val nm = context.getSystemService(NotificationManager::class.java)

    init {
        createChannels()
    }

    private fun createChannels() {
        val status = NotificationChannel(
            CHANNEL_STATUS,
            context.getString(R.string.channel_status_name),
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = context.getString(R.string.channel_status_desc)
        }
        val alerts = NotificationChannel(
            CHANNEL_ALERTS,
            context.getString(R.string.channel_alerts_name),
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = context.getString(R.string.channel_alerts_desc)
            enableVibration(true)
            setSound(
                RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION),
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            )
        }
        nm?.createNotificationChannels(listOf(status, alerts))
    }

    fun buildStatusNotification(data: StatusData): Notification {
        val title = when {
            !data.masterEnabled -> context.getString(R.string.notif_status_disabled)
            data.paused -> context.getString(R.string.notif_status_paused)
            else -> context.getString(R.string.notif_status_title)
        }
        val hotspot = when (data.hotspotOn) {
            true -> "on"
            false -> "off"
            null -> "unknown"
        }
        val lines = buildList {
            add("Hotspot: $hotspot")
            if (data.activeRoutineName != null) add("Routine: ${data.activeRoutineName}")
            if (data.usageBytes != null) {
                val usage = com.iranjan.hotspotscheduler.util.Formatters.formatBytes(data.usageBytes)
                add(
                    if (data.capMb != null) {
                        "Today: $usage / ${com.iranjan.hotspotscheduler.util.Formatters.formatCapMb(data.capMb)}"
                    } else {
                        "Today: $usage"
                    }
                )
            }
            if (data.suppressed) add(context.getString(R.string.suppressed_active))
            if (data.capHitToday) add(context.getString(R.string.cap_hit_active))
            add(
                if (data.accessibilityOk) context.getString(R.string.acc_ok)
                else context.getString(R.string.acc_bad)
            )
        }
        val text = lines.joinToString("  |  ")
        val builder = NotificationCompat.Builder(context, CHANNEL_STATUS)
            .setSmallIcon(R.drawable.ic_app_icon)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(lines.joinToString("\n")))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(mainActivityPendingIntent())
            .addAction(0, context.getString(R.string.notif_action_pause_today), actionPendingIntent(HotspotAutomationService.ACTION_PAUSE_TODAY, 11))
            .addAction(0, context.getString(R.string.notif_action_turn_off), actionPendingIntent(HotspotAutomationService.ACTION_TURN_OFF_NOW, 12))
        return builder.build()
    }

    fun notifyStatus(data: StatusData) {
        nm?.notify(ID_STATUS, buildStatusNotification(data))
    }

    fun notifyToggleFailed() {
        notifyAlert(
            ID_TOGGLE_FAIL,
            context.getString(R.string.notif_toggle_fail_title),
            context.getString(R.string.notif_toggle_fail_text),
            mainActivityPendingIntent()
        )
    }

    fun notifyCapReached(usageFormatted: String, capFormatted: String, toggleFailed: Boolean) {
        val text = if (toggleFailed) {
            context.getString(R.string.notif_cap_fail_text, usageFormatted, capFormatted)
        } else {
            context.getString(R.string.notif_cap_text, usageFormatted, capFormatted)
        }
        notifyAlert(ID_CAP, context.getString(R.string.notif_cap_title), text, mainActivityPendingIntent())
    }

    fun notifyAccessibilityDisabled() {
        val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val pi = PendingIntent.getActivity(context, 21, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        nm?.notify(
            ID_ACC,
            NotificationCompat.Builder(context, CHANNEL_ALERTS)
                .setSmallIcon(R.drawable.ic_app_icon)
                .setContentTitle(context.getString(R.string.notif_acc_disabled_title))
                .setContentText(context.getString(R.string.notif_acc_disabled_text))
                .setStyle(NotificationCompat.BigTextStyle().bigText(context.getString(R.string.notif_acc_disabled_text)))
                .setAutoCancel(true)
                .addAction(0, context.getString(R.string.notif_acc_disabled_action), pi)
                .setContentIntent(pi)
                .build()
        )
    }

    fun postOpenHotspotSettingsPrompt() {
        val pi = PendingIntent.getActivity(
            context,
            22,
            navigator.bestSettingsIntent(),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        nm?.notify(
            ID_OPEN_PROMPT,
            NotificationCompat.Builder(context, CHANNEL_ALERTS)
                .setSmallIcon(R.drawable.ic_app_icon)
                .setContentTitle(context.getString(R.string.notif_open_settings_title))
                .setContentText(context.getString(R.string.notif_open_settings_text))
                .setStyle(NotificationCompat.BigTextStyle().bigText(context.getString(R.string.notif_open_settings_text)))
                .setAutoCancel(true)
                .setContentIntent(pi)
                .build()
        )
    }

    private fun notifyAlert(id: Int, title: String, text: String, contentIntent: PendingIntent?) {
        nm?.notify(
            id,
            NotificationCompat.Builder(context, CHANNEL_ALERTS)
                .setSmallIcon(R.drawable.ic_app_icon)
                .setContentTitle(title)
                .setContentText(text)
                .setStyle(NotificationCompat.BigTextStyle().bigText(text))
                .setAutoCancel(true)
                .setContentIntent(contentIntent)
                .build()
        )
    }

    private fun mainActivityPendingIntent(): PendingIntent =
        PendingIntent.getActivity(
            context,
            20,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

    private fun actionPendingIntent(action: String, requestCode: Int): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            requestCode,
            Intent(context, ActionReceiver::class.java).setAction(action),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

    companion object {
        const val CHANNEL_STATUS = "status"
        const val CHANNEL_ALERTS = "alerts"
        const val ID_STATUS = 1
        const val ID_TOGGLE_FAIL = 2
        const val ID_CAP = 3
        const val ID_ACC = 4
        const val ID_OPEN_PROMPT = 5
    }
}
