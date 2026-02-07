package com.dibe.eduhive.presentation.hiveList.viewmodel


// Events
sealed class HiveListEvent {
    object LoadHives : HiveListEvent()
    data class CreateHive(val name: String, val description: String?) : HiveListEvent()
    data class SelectHive(val hiveId: String) : HiveListEvent()
    object ShowCreateDialog : HiveListEvent()
    object HideCreateDialog : HiveListEvent()
    object ClearError : HiveListEvent()
}