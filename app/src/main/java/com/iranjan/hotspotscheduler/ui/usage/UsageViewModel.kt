package com.iranjan.hotspotscheduler.ui.usage

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.iranjan.hotspotscheduler.data.db.UsageDayEntity
import com.iranjan.hotspotscheduler.data.repo.RoutineRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class UsageViewModel @Inject constructor(
    repo: RoutineRepository
) : ViewModel() {

    val days: StateFlow<List<UsageDayEntity>> = repo.observeUsage(7)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
}
