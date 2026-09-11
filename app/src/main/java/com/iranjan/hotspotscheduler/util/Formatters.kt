package com.iranjan.hotspotscheduler.util

import kotlin.math.roundToLong

object Formatters {

    fun formatBytes(bytes: Long): String {
        if (bytes < 1024) return "$bytes B"
        val kb = bytes / 1024.0
        if (kb < 1024) return "%.1f KB".format(kb)
        val mb = kb / 1024.0
        if (mb < 1024) return "%.1f MB".format(mb)
        return "%.2f GB".format(mb / 1024.0)
    }

    fun formatCapMb(capMb: Long): String =
        if (capMb >= 1024 && capMb % 1024 == 0L) "${capMb / 1024} GB" else "$capMb MB"

    fun unitToMb(value: Double, unitIsGb: Boolean): Long =
        if (unitIsGb) (value * 1024).roundToLong() else value.roundToLong()

    fun mbToUnitValue(capMb: Long, unitIsGb: Boolean): Double =
        if (unitIsGb) capMb / 1024.0 else capMb.toDouble()
}
