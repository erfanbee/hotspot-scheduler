package com.iranjan.hotspotscheduler.core

import com.iranjan.hotspotscheduler.data.model.Routine
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

object RoutineEvaluator {

    data class Boundary(val routineId: Long, val isStart: Boolean, val atMillis: Long) {
        val key: String get() = "$routineId:${if (isStart) "S" else "E"}:$atMillis"
    }

    const val DAY_MINUTES = 24L * 60

    fun windowOnDay(routine: Routine, date: LocalDate, zone: ZoneId): Pair<Long, Long>? {
        if (!routine.enabled) return null
        if (date.dayOfWeek.value !in routine.days) return null
        val start = date.atStartOfDay(zone).plusMinutes(routine.startMinutes.toLong())
        val raw = (routine.endMinutes - routine.startMinutes).toLong()
        val durationMinutes = when {
            raw <= 0 -> raw + DAY_MINUTES
            else -> raw
        }
        return start.toInstant().toEpochMilli() to
            start.plusMinutes(durationMinutes).toInstant().toEpochMilli()
    }

    private fun collectWindows(routine: Routine, firstDay: LocalDate, dayCount: Int, zone: ZoneId): List<Pair<Long, Long>> =
        (0 until dayCount).mapNotNull { offset ->
            windowOnDay(routine, firstDay.plusDays(offset.toLong()), zone)
        }

    fun activeRoutines(
        routines: List<Routine>,
        nowMs: Long,
        zone: ZoneId = ZoneId.systemDefault()
    ): List<Routine> {
        val today = Instant.ofEpochMilli(nowMs).atZone(zone).toLocalDate()
        return routines.filter { routine ->
            collectWindows(routine, today.minusDays(1), 2, zone).any { (start, end) -> nowMs >= start && nowMs < end }
        }
    }

    fun strictestCapMb(routines: List<Routine>): Long? =
        routines.filter { it.enabled }.mapNotNull { it.capMb }.minOrNull()

    fun nextBoundary(
        routines: List<Routine>,
        nowMs: Long,
        zone: ZoneId = ZoneId.systemDefault()
    ): Boundary? {
        val today = Instant.ofEpochMilli(nowMs).atZone(zone).toLocalDate()
        var best: Boundary? = null
        for (routine in routines) {
            if (!routine.enabled) continue
            for (window in collectWindows(routine, today, 8, zone)) {
                if (window.first > nowMs) {
                    val candidate = Boundary(routine.id, true, window.first)
                    if (best == null || candidate.atMillis < best!!.atMillis) best = candidate
                }
                if (window.second > nowMs) {
                    val candidate = Boundary(routine.id, false, window.second)
                    if (best == null || candidate.atMillis < best!!.atMillis) best = candidate
                }
            }
        }
        return best
    }

    fun lastBoundary(
        routines: List<Routine>,
        nowMs: Long,
        zone: ZoneId = ZoneId.systemDefault()
    ): Boundary? {
        val today = Instant.ofEpochMilli(nowMs).atZone(zone).toLocalDate()
        var best: Boundary? = null
        for (routine in routines) {
            if (!routine.enabled) continue
            for (window in collectWindows(routine, today.minusDays(2), 3, zone)) {
                if (window.first <= nowMs) {
                    val candidate = Boundary(routine.id, true, window.first)
                    if (best == null || candidate.atMillis > best!!.atMillis) best = candidate
                }
                if (window.second <= nowMs) {
                    val candidate = Boundary(routine.id, false, window.second)
                    if (best == null || candidate.atMillis > best!!.atMillis) best = candidate
                }
            }
        }
        return best
    }

    fun overlapping(
        routine: Routine,
        others: List<Routine>,
        zone: ZoneId = ZoneId.systemDefault()
    ): List<Routine> {
        if (!routine.enabled) return emptyList()
        val today = LocalDate.now(zone)
        return others.filter { other ->
            other.enabled && windowsOverlap(routine, other, today, zone)
        }
    }

    private fun windowsOverlap(a: Routine, b: Routine, today: LocalDate, zone: ZoneId): Boolean {
        val aWindows = collectWindows(a, today, 9, zone)
        val bWindows = collectWindows(b, today, 9, zone)
        return aWindows.any { wa -> bWindows.any { wb -> wa.first < wb.second && wb.first < wa.second } }
    }

    fun nextMidnight(nowMs: Long, zone: ZoneId = ZoneId.systemDefault()): Long {
        val now = Instant.ofEpochMilli(nowMs).atZone(zone)
        return now.toLocalDate().plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
    }

    fun todayEpochDay(nowMs: Long, zone: ZoneId = ZoneId.systemDefault()): Long =
        Instant.ofEpochMilli(nowMs).atZone(zone).toLocalDate().toEpochDay()

    fun summarize(routine: Routine, dayLabels: Map<Int, String>): String {
        val order = listOf(1, 2, 3, 4, 5, 6, 7)
        val days = if (routine.days.toSortedSet() == order.toSet()) {
            "Every day"
        } else {
            order.filter { it in routine.days }.mapNotNull { dayLabels[it] }.joinToString(", ")
        }
        return "$days ${formatMinutes(routine.startMinutes)}–${formatMinutes(routine.endMinutes)}"
    }

    fun formatMinutes(minutes: Int): String {
        val h = minutes / 60
        val m = minutes % 60
        return "%02d:%02d".format(h, m)
    }

    fun dayLabel(isoDay: Int, labels: Map<Int, String>): String = labels[isoDay] ?: isoDay.toString()

    fun nowZoned(nowMs: Long, zone: ZoneId = ZoneId.systemDefault()): ZonedDateTime =
        Instant.ofEpochMilli(nowMs).atZone(zone)
}
