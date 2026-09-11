package com.iranjan.hotspotscheduler.ui.editor

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.iranjan.hotspotscheduler.R
import com.iranjan.hotspotscheduler.core.AlarmScheduler
import com.iranjan.hotspotscheduler.data.model.Routine
import com.iranjan.hotspotscheduler.data.repo.RoutineRepository
import com.iranjan.hotspotscheduler.util.Formatters
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class RoutineDraft(
    val id: Long = 0,
    val name: String = "",
    val days: Set<Int> = setOf(1, 2, 3, 4, 5),
    val startMinutes: Int = 8 * 60,
    val endMinutes: Int = 9 * 60 + 30,
    val capText: String = "",
    val capIsGb: Boolean = true,
    val enabled: Boolean = true,
    val mobileData: Boolean = false,
    val hotspotPassword: String = ""
)

@HiltViewModel
class RoutineEditorViewModel @Inject constructor(
    private val repo: RoutineRepository,
    private val alarmScheduler: AlarmScheduler,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val _draft = MutableStateFlow(RoutineDraft())
    val draft: StateFlow<RoutineDraft> = _draft

    private val _overlapNames = MutableStateFlow<List<String>>(emptyList())
    val overlapNames: StateFlow<List<String>> = _overlapNames

    fun load(routineId: Long) = viewModelScope.launch {
        if (routineId <= 0) {
            refreshOverlaps()
            return@launch
        }
        repo.routine(routineId)?.let { r ->
            _draft.value = RoutineDraft(
                id = r.id,
                name = r.name,
                days = r.days,
                startMinutes = r.startMinutes,
                endMinutes = r.endMinutes,
                capText = r.capMb?.let { formatCapText(it) } ?: "",
                capIsGb = (r.capMb ?: 0L) >= 1024,
                enabled = r.enabled,
                mobileData = r.mobileData,
                hotspotPassword = r.hotspotPassword ?: ""
            )
        }
        refreshOverlaps()
    }

    private fun formatCapText(capMb: Long): String =
        if (capMb >= 1024) {
            val gb = capMb / 1024.0
            if (gb % 1.0 == 0.0) gb.toInt().toString() else "%.1f".format(gb)
        } else {
            capMb.toString()
        }

    fun update(transform: (RoutineDraft) -> RoutineDraft) {
        _draft.value = transform(_draft.value)
        refreshOverlaps()
    }

    fun toggleDay(iso: Int) = update { d ->
        d.copy(days = if (iso in d.days) d.days - iso else d.days + iso)
    }

    private fun refreshOverlaps() {
        viewModelScope.launch {
            val d = _draft.value
            val capMb = capMbOf(d)
            val temp = Routine(
                id = d.id,
                name = d.name.ifBlank { context.getString(R.string.editor_name) },
                days = d.days,
                startMinutes = d.startMinutes,
                endMinutes = d.endMinutes,
                capMb = capMb,
                enabled = d.enabled
            )
            _overlapNames.value = repo.overlappingWith(temp).map { it.name }
        }
    }

    private fun capMbOf(d: RoutineDraft): Long? =
        d.capText.toDoubleOrNull()?.let { Formatters.unitToMb(it, d.capIsGb) }?.takeIf { it > 0 }

    fun save(onDone: () -> Unit) = viewModelScope.launch {
        val d = _draft.value
        val routine = Routine(
            id = d.id,
            name = d.name.ifBlank { context.getString(R.string.editor_name) },
            days = d.days.ifEmpty { setOf(1, 2, 3, 4, 5) },
            startMinutes = d.startMinutes,
            endMinutes = d.endMinutes,
            capMb = capMbOf(d),
            enabled = d.enabled,
            mobileData = d.mobileData,
            hotspotPassword = d.hotspotPassword.trim().takeIf { it.isNotEmpty() }
        )
        repo.save(routine)
        alarmScheduler.rescheduleAll()
        onDone()
    }

    fun delete(onDone: () -> Unit) = viewModelScope.launch {
        if (_draft.value.id > 0) repo.delete(_draft.value.id)
        alarmScheduler.rescheduleAll()
        onDone()
    }
}
