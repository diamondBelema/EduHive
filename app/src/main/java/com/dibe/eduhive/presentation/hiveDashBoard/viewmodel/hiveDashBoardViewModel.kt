package com.dibe.eduhive.presentation.hiveDashBoard.viewmodel


import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dibe.eduhive.domain.usecase.dashboard.DashboardOverview
import com.dibe.eduhive.domain.usecase.dashboard.GetDashboardOverviewUseCase
import com.dibe.eduhive.domain.repository.QuizRepository
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
    private val quizRepository: QuizRepository,
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
                    // Fetch total quizzes for this hive manually until usecase is updated
                    val concepts = overview.weakConcepts // This isn't all concepts, but we can't easily get all here
                    // Better: Get all concepts first, then map to quizzes. 
                    // For now, let's assume we'll update the overview data class later.
                    
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

