package com.dibe.eduhive.presentation.hiveDashBoard.viewmodel

// Events
sealed class HiveDashboardEvent {
    object Refresh : HiveDashboardEvent()
    object ClearError : HiveDashboardEvent()
}