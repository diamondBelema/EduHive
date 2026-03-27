package com.dibe.eduhive.presentation.hiveDashBoard.viewmodel

import com.dibe.eduhive.domain.usecase.dashboard.DashboardOverview
import com.dibe.eduhive.manager.TaskProgress

// State
data class HiveDashboardState(
    val overview: DashboardOverview? = null,
    val isLoading: Boolean = false,
    val error: String? = null,
    val activeTasks: List<TaskProgress> = emptyList()
)