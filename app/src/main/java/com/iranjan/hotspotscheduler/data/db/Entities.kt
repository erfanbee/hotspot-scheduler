package com.iranjan.hotspotscheduler.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "routines")
data class RoutineEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val daysCsv: String,
    val startMinutes: Int,
    val endMinutes: Int,
    val capMb: Long?,
    val enabled: Boolean,
    val mobileData: Boolean = false,
    val hotspotPassword: String? = null,
    val createdAt: Long
)

@Entity(tableName = "usage_days")
data class UsageDayEntity(
    @PrimaryKey val epochDay: Long,
    val bytes: Long,
    val updatedAt: Long
)
