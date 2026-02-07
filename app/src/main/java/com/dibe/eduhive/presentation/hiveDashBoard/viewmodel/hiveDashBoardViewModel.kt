package com.dibe.eduhive.presentation.hiveDashBoard.viewmodel


import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dibe.eduhive.domain.usecase.dashboard.DashboardOverview
import com.dibe.eduhive.domain.usecase.dashboard.GetDashboardOverviewUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class HiveDashboardViewModel @Inject constructor(
    private val getDashboardOverviewUseCase: GetDashboardOverviewUseCase,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val hiveId: String = checkNotNull(savedStateHandle["hiveId"])

    private val _state = MutableStateFlow(HiveDashboardState())
    val state: StateFlow<HiveDashboardState> = _state.asStateFlow()

    init {
        loadDashboard()
    }

    fun onEvent(event: HiveDashboardEvent) {
        when (event) {
            is HiveDashboardEvent.Refresh -> loadDashboard()
            is HiveDashboardEvent.ClearError -> {
                _state.update { it.copy(error = null) }
            }
        }
    }

    private fun loadDashboard() {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true) }

            getDashboardOverviewUseCase(hiveId).fold(
                onSuccess = { overview ->
                    _state.update {
                        it.copy(
                            overview = overview,
                            isLoading = false,
                            error = null
                        )
                    }
                },
                onFailure = { error ->
                    _state.update {
                        it.copy(
                            isLoading = false,
                            error = error.message ?: "Failed to load dashboard"
                        )
                    }
                }
            )
        }
    }
}



