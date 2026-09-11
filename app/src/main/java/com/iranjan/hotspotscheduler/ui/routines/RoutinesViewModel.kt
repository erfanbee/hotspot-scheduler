package com.iranjan.hotspotscheduler.ui.routines

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.iranjan.hotspotscheduler.R
import com.iranjan.hotspotscheduler.core.AlarmScheduler
import com.iranjan.hotspotscheduler.data.model.Routine
import com.iranjan.hotspotscheduler.data.prefs.AutomationPrefs
import com.iranjan.hotspotscheduler.data.repo.RoutineRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import android.content.Context
import javax.inject.Inject

@HiltViewModel
class RoutinesViewModel @Inject constructor(
    private val repo: RoutineRepository,
    private val prefs: AutomationPrefs,
    private val alarmScheduler: AlarmScheduler,
    @ApplicationContext private val context: Context
) : ViewModel() {

    val routines: StateFlow<List<Routine>> = repo.observeRoutines()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val masterEnabled: StateFlow<Boolean> = prefs.masterEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    private val _overlapMessages = MutableStateFlow<List<String>>(emptyList())
    val overlapMessages: StateFlow<List<String>> = _overlapMessages

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message

    init {
        viewModelScope.launch {
            routines.collect { refreshOverlaps() }
        }
    }

    private suspend fun refreshOverlaps() {
        _overlapMessages.value = repo.overlappingPairs().map { (a, b) -> "${a.name} overlaps ${b.name}" }
    }

    fun setMaster(enabled: Boolean) = viewModelScope.launch {
        prefs.setMasterEnabled(enabled)
        if (enabled) alarmScheduler.rescheduleAll() else alarmScheduler.cancelAll()
    }

    fun toggleRoutine(routine: Routine, enabled: Boolean) = viewModelScope.launch {
        repo.save(routine.copy(enabled = enabled))
        alarmScheduler.rescheduleAll()
    }

    fun delete(routine: Routine) = viewModelScope.launch {
        repo.delete(routine.id)
        alarmScheduler.rescheduleAll()
    }

    fun export(uri: Uri) = viewModelScope.launch {
        runCatching {
            context.contentResolver.openOutputStream(uri)?.use { it.write(repo.exportJson().toByteArray()) }
        }
        _message.value = context.getString(R.string.exported_toast)
    }

    fun import(uri: Uri) = viewModelScope.launch {
        val result = runCatching {
            context.contentResolver.openInputStream(uri)?.use { it.readBytes().decodeToString() }
                ?.let { repo.importJson(it) } ?: 0
        }
        _message.value = result.fold(
            onSuccess = { context.getString(R.string.imported_toast, it) },
            onFailure = { context.getString(R.string.import_failed_toast) }
        )
        runCatching { alarmScheduler.rescheduleAll() }
    }

    fun clearMessage() {
        _message.value = null
    }
}
