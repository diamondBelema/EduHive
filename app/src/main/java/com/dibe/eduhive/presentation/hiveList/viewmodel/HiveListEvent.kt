package com.dibe.eduhive.presentation.hiveList.viewmodel

import com.dibe.eduhive.domain.model.Hive

// Events
sealed class HiveListEvent {
    object LoadHives : HiveListEvent()
    data class CreateHive(val name: String, val description: String?) : HiveListEvent()
    data class SelectHive(val hiveId: String) : HiveListEvent()
    object ClearSelectedHive : HiveListEvent()
    object ShowCreateDialog : HiveListEvent()
    object HideCreateDialog : HiveListEvent()
    // Edit
    data class ShowEditDialog(val hive: Hive) : HiveListEvent()
    object HideEditDialog : HiveListEvent()
    data class EditHive(val hiveId: String, val name: String, val description: String?) : HiveListEvent()
    // Delete
    data class ShowDeleteConfirm(val hive: Hive) : HiveListEvent()
    object HideDeleteConfirm : HiveListEvent()
    data class DeleteHive(val hiveId: String) : HiveListEvent()
    // Archive
    data class ArchiveHive(val hiveId: String) : HiveListEvent()
    object ClearError : HiveListEvent()
}