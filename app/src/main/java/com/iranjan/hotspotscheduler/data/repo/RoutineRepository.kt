package com.iranjan.hotspotscheduler.data.repo

import com.iranjan.hotspotscheduler.core.RoutineEvaluator
import com.iranjan.hotspotscheduler.data.db.RoutineEntity
import com.iranjan.hotspotscheduler.data.db.RoutineDao
import com.iranjan.hotspotscheduler.data.db.UsageDao
import com.iranjan.hotspotscheduler.data.db.UsageDayEntity
import com.iranjan.hotspotscheduler.data.model.Routine
import com.iranjan.hotspotscheduler.data.model.toDomain
import com.iranjan.hotspotscheduler.data.model.toEntity
import com.iranjan.hotspotscheduler.data.prefs.AutomationPrefs
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RoutineRepository @Inject constructor(
    private val routineDao: RoutineDao,
    private val usageDao: UsageDao,
    private val prefs: AutomationPrefs
) {
    fun observeRoutines(): Flow<List<Routine>> =
        routineDao.observeAll().map { list -> list.map { it.toDomain() } }

    fun observeUsage(limit: Int = 7): Flow<List<UsageDayEntity>> = usageDao.observeRecent(limit)

    suspend fun routine(id: Long): Routine? = routineDao.getById(id)?.toDomain()

    suspend fun enabledRoutines(): List<Routine> = routineDao.enabledOnce().map { it.toDomain() }

    suspend fun save(routine: Routine) {
        routineDao.upsert(routine.toEntity(System.currentTimeMillis()))
    }

    suspend fun delete(id: Long) = routineDao.delete(id)

    suspend fun overlappingWith(routine: Routine): List<Routine> {
        val all = routineDao.allOnce().map { it.toDomain() }
        return RoutineEvaluator.overlapping(routine, all.filter { it.id != routine.id })
    }

    suspend fun overlappingPairs(): List<Pair<Routine, Routine>> {
        val all = routineDao.allOnce().map { it.toDomain() }
        val result = mutableListOf<Pair<Routine, Routine>>()
        for (i in all.indices) {
            for (j in i + 1 until all.size) {
                if (RoutineEvaluator.overlapping(all[i], listOf(all[j])).isNotEmpty()) {
                    result.add(all[i] to all[j])
                }
            }
        }
        return result
    }

    suspend fun upsertUsageDay(epochDay: Long, bytes: Long) {
        usageDao.upsert(UsageDayEntity(epochDay, bytes, System.currentTimeMillis()))
    }

    suspend fun pruneUsage(beforeEpochDay: Long) = usageDao.prune(beforeEpochDay)

    suspend fun latestUsage(): UsageDayEntity? = usageDao.latest()

    suspend fun exportJson(): String {
        val routines = routineDao.allOnce().map { it.toDomain() }
        val root = JSONObject()
        root.put("version", 1)
        val array = JSONArray()
        for (r in routines) {
            val obj = JSONObject()
            obj.put("name", r.name)
            obj.put("days", JSONArray(r.days.sorted()))
            obj.put("startMinutes", r.startMinutes)
            obj.put("endMinutes", r.endMinutes)
            obj.put("capMb", r.capMb ?: JSONObject.NULL)
            obj.put("enabled", r.enabled)
            array.put(obj)
        }
        root.put("routines", array)
        return root.toString(2)
    }

    suspend fun importJson(text: String): Int {
        val root = JSONObject(text)
        val array = root.optJSONArray("routines") ?: throw IllegalArgumentException("missing routines array")
        var count = 0
        for (i in 0 until array.length()) {
            val obj = array.getJSONObject(i)
            val routine = Routine(
                id = 0,
                name = obj.optString("name", "Imported ${i + 1}"),
                days = obj.optJSONArray("days")?.let { arr -> (0 until arr.length()).map { arr.getInt(it) }.toSet() }
                    ?: setOf(1, 2, 3, 4, 5),
                startMinutes = obj.optInt("startMinutes", 480),
                endMinutes = obj.optInt("endMinutes", 570),
                capMb = if (obj.isNull("capMb")) null else obj.optLong("capMb"),
                enabled = obj.optBoolean("enabled", true)
            )
            routineDao.upsert(routine.toEntity(System.currentTimeMillis()))
            count++
        }
        return count
    }
}
