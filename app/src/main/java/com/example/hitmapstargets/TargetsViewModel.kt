package com.example.hitmapstargets

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch


data class TargetsUiState(
    val targets: List<ElusiveTarget> = emptyList(),
    val loading: Boolean = true,
    val refreshing: Boolean = false,
    val error: String? = null,
)

class TargetsViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = TargetsRepository(application)
    private val mutableState = MutableStateFlow(
        TargetsUiState(targets = repository.cached(), loading = repository.cached().isEmpty())
    )
    val state: StateFlow<TargetsUiState> = mutableState
    private var refreshJob: Job? = null

    init {
        refresh()
        viewModelScope.launch {
            while (true) {
                delay(5 * 60_000L)
                refresh()
            }
        }
    }

    fun refresh() {
        if (refreshJob?.isActive == true) return
        refreshJob = viewModelScope.launch {
            mutableState.value = mutableState.value.copy(
                refreshing = true, loading = mutableState.value.targets.isEmpty(), error = null
            )
            runCatching { repository.fetch() }
                .onSuccess { targets ->
                    mutableState.value = TargetsUiState(targets = targets, loading = false)
                    TargetAlerts.syncSchedules(getApplication(), targets)
                }
                .onFailure { error ->
                    val cached = repository.cached()
                    mutableState.value = TargetsUiState(
                        targets = cached, loading = false,
                        error = error.localizedMessage ?: "Не удалось обновить список"
                    )
                }
        }
    }
}
