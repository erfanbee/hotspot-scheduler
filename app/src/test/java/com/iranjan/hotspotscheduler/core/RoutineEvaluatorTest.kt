package com.iranjan.hotspotscheduler.core

import com.iranjan.hotspotscheduler.data.model.Routine
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RoutineEvaluatorTest {

    private val zone: ZoneId = ZoneId.of("Europe/Berlin")

    private fun at(date: String, time: String): Long =
        ZonedDateTime.of(LocalDate.parse(date), LocalTime.parse(time), zone).toInstant().toEpochMilli()

    private fun routine(
        days: Set<Int>,
        startMinutes: Int,
        endMinutes: Int,
        capMb: Long? = null,
        enabled: Boolean = true
    ) = Routine(1, "r", days, startMinutes, endMinutes, capMb, enabled)

    @Test
    fun `window on matching day`() {
        val window = RoutineEvaluator.windowOnDay(routine(setOf(1), 8 * 60, 9 * 60 + 30), LocalDate.parse("2026-09-07"), zone)!!
        assertEquals(at("2026-09-07", "08:00"), window.first)
        assertEquals(at("2026-09-07", "09:30"), window.second)
    }

    @Test
    fun `overnight window crosses midnight`() {
        val window = RoutineEvaluator.windowOnDay(routine(setOf(1), 22 * 60, 2 * 60), LocalDate.parse("2026-09-07"), zone)!!
        assertEquals(at("2026-09-07", "22:00"), window.first)
        assertEquals(at("2026-09-08", "02:00"), window.second)
    }

    @Test
    fun `wrong day returns null`() {
        assertNull(RoutineEvaluator.windowOnDay(routine(setOf(1), 8 * 60, 9 * 60), LocalDate.parse("2026-09-08"), zone))
    }

    @Test
    fun `disabled routine returns null`() {
        assertNull(RoutineEvaluator.windowOnDay(routine(setOf(1), 8 * 60, 9 * 60, enabled = false), LocalDate.parse("2026-09-07"), zone))
    }

    @Test
    fun `active inside window`() {
        val routines = listOf(routine(setOf(1, 2, 3, 4, 5), 8 * 60, 9 * 60 + 30))
        assertTrue(RoutineEvaluator.activeRoutines(routines, at("2026-09-07", "08:30"), zone).isNotEmpty())
        assertTrue(RoutineEvaluator.activeRoutines(routines, at("2026-09-07", "08:00"), zone).isNotEmpty())
    }

    @Test
    fun `not active outside window`() {
        val routines = listOf(routine(setOf(1, 2, 3, 4, 5), 8 * 60, 9 * 60 + 30))
        assertTrue(RoutineEvaluator.activeRoutines(routines, at("2026-09-07", "10:00"), zone).isEmpty())
        assertTrue(RoutineEvaluator.activeRoutines(routines, at("2026-09-07", "07:59"), zone).isEmpty())
    }

    @Test
    fun `overnight routine is active after midnight`() {
        val routines = listOf(routine(setOf(1), 22 * 60, 2 * 60))
        assertTrue(RoutineEvaluator.activeRoutines(routines, at("2026-09-08", "01:00"), zone).isNotEmpty())
        assertTrue(RoutineEvaluator.activeRoutines(routines, at("2026-09-08", "02:01"), zone).isEmpty())
    }

    @Test
    fun `next boundary picks earliest start`() {
        val routines = listOf(routine(setOf(1, 2, 3, 4, 5), 8 * 60, 9 * 60 + 30))
        val boundary = RoutineEvaluator.nextBoundary(routines, at("2026-09-07", "07:00"), zone)!!
        assertTrue(boundary.isStart)
        assertEquals(at("2026-09-07", "08:00"), boundary.atMillis)
    }

    @Test
    fun `next boundary picks end before next start`() {
        val routines = listOf(routine(setOf(1, 2, 3, 4, 5), 8 * 60, 9 * 60 + 30))
        val boundary = RoutineEvaluator.nextBoundary(routines, at("2026-09-07", "09:00"), zone)!!
        assertFalse(boundary.isStart)
        assertEquals(at("2026-09-07", "09:30"), boundary.atMillis)
    }

    @Test
    fun `last boundary is most recent past event`() {
        val routines = listOf(routine(setOf(1, 2, 3, 4, 5), 8 * 60, 9 * 60 + 30))
        val boundary = RoutineEvaluator.lastBoundary(routines, at("2026-09-07", "08:30"), zone)!!
        assertTrue(boundary.isStart)
        assertEquals(at("2026-09-07", "08:00"), boundary.atMillis)
    }

    @Test
    fun `strictest cap wins and null when no caps`() {
        assertEquals(1024L, RoutineEvaluator.strictestCapMb(listOf(routine(setOf(1), 0, 60, 2048), routine(setOf(1), 0, 60, 1024))))
        assertNull(RoutineEvaluator.strictestCapMb(listOf(routine(setOf(1), 0, 60))))
    }

    @Test
    fun `overlapping windows detected`() {
        val a = routine(setOf(1), 8 * 60, 9 * 60 + 30)
        val b = routine(setOf(1), 9 * 60, 10 * 60)
        assertTrue(RoutineEvaluator.overlapping(b, listOf(a)).contains(a))
    }

    @Test
    fun `touching windows do not overlap`() {
        val a = routine(setOf(1), 8 * 60, 9 * 60)
        val b = routine(setOf(1), 9 * 60, 10 * 60)
        assertTrue(RoutineEvaluator.overlapping(b, listOf(a)).isEmpty())
    }

    @Test
    fun `overnight overlap detected`() {
        val a = routine(setOf(1), 22 * 60, 2 * 60)
        val b = routine(setOf(2), 1 * 60, 3 * 60)
        assertTrue(RoutineEvaluator.overlapping(b, listOf(a)).contains(a))
    }

    @Test
    fun `next midnight is start of next day`() {
        assertEquals(at("2026-09-08", "00:00"), RoutineEvaluator.nextMidnight(at("2026-09-07", "23:59"), zone))
        assertEquals(at("2026-09-08", "00:00"), RoutineEvaluator.nextMidnight(at("2026-09-07", "00:00"), zone))
    }

    @Test
    fun `dst spring forward keeps wall-clock start and duration based end`() {
        val window = RoutineEvaluator.windowOnDay(routine(setOf(7), 1 * 60, 4 * 60), LocalDate.parse("2026-03-29"), zone)!!
        assertEquals(at("2026-03-29", "01:00"), window.first)
        assertEquals(3 * 3_600_000L, window.second - window.first)
    }

    @Test
    fun `dst fall back also keeps duration`() {
        val window = RoutineEvaluator.windowOnDay(routine(setOf(7), 1 * 60, 4 * 60), LocalDate.parse("2026-10-25"), zone)!!
        assertEquals(at("2026-10-25", "01:00"), window.first)
        assertEquals(3 * 3_600_000L, window.second - window.first)
    }
}
