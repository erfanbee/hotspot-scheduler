package com.iranjan.hotspotscheduler.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface RoutineDao {
    @Query("SELECT * FROM routines ORDER BY createdAt")
    fun observeAll(): Flow<List<RoutineEntity>>

    @Query("SELECT * FROM routines")
    suspend fun allOnce(): List<RoutineEntity>

    @Query("SELECT * FROM routines WHERE enabled = 1")
    suspend fun enabledOnce(): List<RoutineEntity>

    @Query("SELECT * FROM routines WHERE id = :id")
    suspend fun getById(id: Long): RoutineEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: RoutineEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(entities: List<RoutineEntity>)

    @Query("DELETE FROM routines WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM routines")
    suspend fun deleteAll()
}

@Dao
interface UsageDao {
    @Query("SELECT * FROM usage_days ORDER BY epochDay DESC LIMIT :limit")
    fun observeRecent(limit: Int): Flow<List<UsageDayEntity>>

    @Query("SELECT * FROM usage_days WHERE epochDay = :epochDay")
    suspend fun getDay(epochDay: Long): UsageDayEntity?

    @Query("SELECT * FROM usage_days ORDER BY epochDay DESC LIMIT 1")
    suspend fun latest(): UsageDayEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: UsageDayEntity)

    @Query("DELETE FROM usage_days WHERE epochDay < :beforeEpochDay")
    suspend fun prune(beforeEpochDay: Long)
}
