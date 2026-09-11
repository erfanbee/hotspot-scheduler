package com.iranjan.hotspotscheduler.data.model

import com.iranjan.hotspotscheduler.data.db.RoutineEntity
import com.iranjan.hotspotscheduler.util.PasswordCrypto

data class Routine(
    val id: Long,
    val name: String,
    val days: Set<Int>,
    val startMinutes: Int,
    val endMinutes: Int,
    val capMb: Long?,
    val enabled: Boolean,
    val mobileData: Boolean = false,
    val hotspotPassword: String? = null
)

fun RoutineEntity.toDomain(): Routine = Routine(
    id = id,
    name = name,
    days = daysCsv.split(",").filter { it.isNotBlank() }.map { it.trim().toInt() }.toSet(),
    startMinutes = startMinutes,
    endMinutes = endMinutes,
    capMb = capMb,
    enabled = enabled,
    mobileData = mobileData,
    hotspotPassword = PasswordCrypto.decrypt(hotspotPassword)
)

fun Routine.toEntity(createdAt: Long): RoutineEntity = RoutineEntity(
    id = id,
    name = name,
    daysCsv = days.sorted().joinToString(","),
    startMinutes = startMinutes,
    endMinutes = endMinutes,
    capMb = capMb,
    enabled = enabled,
    mobileData = mobileData,
    hotspotPassword = PasswordCrypto.encrypt(hotspotPassword ?: "").ifBlank { null },
    createdAt = createdAt
)
