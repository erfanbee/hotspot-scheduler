package com.iranjan.hotspotscheduler.data.prefs

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.iranjan.hotspotscheduler.data.model.CALIB_TYPE_CLASS_SIG
import com.iranjan.hotspotscheduler.data.model.CalibrationSignature
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AutomationPrefs @Inject constructor(private val dataStore: DataStore<Preferences>) {

    private object Keys {
        val MASTER_ENABLED = booleanPreferencesKey("master_enabled")
        val PAUSED_UNTIL_MS = longPreferencesKey("paused_until_ms")
        val SUPPRESSED = booleanPreferencesKey("suppressed_until_next_window")
        val CAP_HIT_DAY = longPreferencesKey("cap_hit_epoch_day")
        val LAST_BOUNDARY = stringPreferencesKey("last_applied_boundary")
        val HOTSPOT_KNOWN = intPreferencesKey("hotspot_known_state")
        val ACC_ALERT_MS = longPreferencesKey("last_acc_alert_ms")
        val CALIB_TYPE = stringPreferencesKey("calib_type")
        val CALIB_VALUE = stringPreferencesKey("calib_value")
    }

    val masterEnabled: Flow<Boolean> = dataStore.data.map { it[Keys.MASTER_ENABLED] ?: true }
    val pausedUntilMs: Flow<Long> = dataStore.data.map { it[Keys.PAUSED_UNTIL_MS] ?: 0L }
    val suppressedUntilNextWindow: Flow<Boolean> = dataStore.data.map { it[Keys.SUPPRESSED] ?: false }
    val capHitEpochDay: Flow<Long> = dataStore.data.map { it[Keys.CAP_HIT_DAY] ?: 0L }
    val lastAppliedBoundary: Flow<String> = dataStore.data.map { it[Keys.LAST_BOUNDARY] ?: "" }
    val lastKnownHotspotOn: Flow<Boolean?> =
        dataStore.data.map { v -> when (v[Keys.HOTSPOT_KNOWN] ?: -1) { 0 -> false; 1 -> true; else -> null } }
    val lastAccAlertMs: Flow<Long> = dataStore.data.map { it[Keys.ACC_ALERT_MS] ?: 0L }

    suspend fun setMasterEnabled(value: Boolean) =
        dataStore.edit { it[Keys.MASTER_ENABLED] = value }

    suspend fun setPausedUntilMs(value: Long) =
        dataStore.edit { it[Keys.PAUSED_UNTIL_MS] = value }

    suspend fun setSuppressedUntilNextWindow(value: Boolean) =
        dataStore.edit { it[Keys.SUPPRESSED] = value }

    suspend fun setCapHitEpochDay(value: Long) =
        dataStore.edit { it[Keys.CAP_HIT_DAY] = value }

    suspend fun setLastAppliedBoundary(value: String) =
        dataStore.edit { it[Keys.LAST_BOUNDARY] = value }

    suspend fun setLastKnownHotspotOn(value: Boolean?) =
        dataStore.edit { it[Keys.HOTSPOT_KNOWN] = when (value) { true -> 1; false -> 0; null -> -1 } }

    suspend fun setLastAccAlertMs(value: Long) =
        dataStore.edit { it[Keys.ACC_ALERT_MS] = value }

    private val calibrationType: Flow<String> = dataStore.data.map { it[Keys.CALIB_TYPE] ?: "" }
    private val calibrationValue: Flow<String> = dataStore.data.map { it[Keys.CALIB_VALUE] ?: "" }

    suspend fun calibration(): CalibrationSignature? {
        val type = calibrationType.first()
        val value = calibrationValue.first()
        if (type.isBlank() || value.isBlank()) return null
        return CalibrationSignature(type, value)
    }

    suspend fun setCalibration(signature: CalibrationSignature) =
        dataStore.edit {
            it[Keys.CALIB_TYPE] = if (signature.type == CALIB_TYPE_CLASS_SIG) CALIB_TYPE_CLASS_SIG else "RID"
            it[Keys.CALIB_VALUE] = signature.value
        }

    suspend fun clearCalibration() =
        dataStore.edit {
            it[Keys.CALIB_TYPE] = ""
            it[Keys.CALIB_VALUE] = ""
        }
}
