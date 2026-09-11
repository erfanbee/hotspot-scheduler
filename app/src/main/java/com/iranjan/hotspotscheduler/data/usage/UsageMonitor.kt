package com.iranjan.hotspotscheduler.data.usage

import android.app.usage.NetworkStats
import android.app.usage.NetworkStatsManager
import android.content.Context
import android.net.ConnectivityManager
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

data class UsageSample(val bytes: Long, val source: String)

@Singleton
class UsageMonitor @Inject constructor(@ApplicationContext private val context: Context) {

    private val nsm: NetworkStatsManager? =
        context.getSystemService(NetworkStatsManager::class.java)

    suspend fun hotspotBytesBetween(startMs: Long, endMs: Long = System.currentTimeMillis()): UsageSample? =
        withContext(Dispatchers.IO) {
            queryTetherUids(startMs, endMs) ?: queryMobileTotal(startMs, endMs)
        }

    suspend fun hotspotBytesSinceMidnight(): UsageSample? =
        hotspotBytesBetween(
            java.time.ZonedDateTime.now().toLocalDate()
                .atStartOfDay().atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
        )

    private fun queryTetherUids(startMs: Long, endMs: Long): UsageSample? {
        return try {
            val stats = nsm?.querySummary(ConnectivityManager.TYPE_MOBILE, null, startMs, endMs)
            if (stats == null) {
                null
            } else {
                var total = 0L
                val bucket = NetworkStats.Bucket()
                while (stats.hasNextBucket()) {
                    stats.getNextBucket(bucket)
                    if (bucket.uid in TETHER_UIDS) {
                        total += bucket.rxBytes + bucket.txBytes
                    }
                }
                stats.close()
                if (total > 0) UsageSample(total, "tether-uids") else null
            }
        } catch (t: Throwable) {
            null
        }
    }

    private fun queryMobileTotal(startMs: Long, endMs: Long): UsageSample? {
        return try {
            val bucket = nsm?.querySummaryForDevice(ConnectivityManager.TYPE_MOBILE, null, startMs, endMs)
            if (bucket == null) {
                null
            } else {
                UsageSample((bucket.rxBytes + bucket.txBytes).coerceAtLeast(0L), "mobile-total")
            }
        } catch (t: Throwable) {
            null
        }
    }

    companion object {
        private const val NETWORK_STACK_UID = 1073
        val TETHER_UIDS = setOf(0, NETWORK_STACK_UID)
    }
}
