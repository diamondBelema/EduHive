package com.dibe.eduhive.presentation.hiveList.viewmodel

import com.dibe.eduhive.domain.model.Hive


// State
data class HiveListState(
    val hives: List<Hive> = emptyList(),
    val isLoading: Boolean = false,
    val showCreateDialog: Boolean = false,
    val selectedHiveId: String? = null,
    val hiveToEdit: Hive? = null,
    val hiveToDelete: Hive? = null,
    val error: String? = null,
    val searchQuery: String = "",
    val isSearchActive: Boolean = false,
    val archivedHives: List<Hive> = emptyList(),
    val showArchiveSheet: Boolean = false,
    val isLoadingArchived: Boolean = false
) {
    val filteredHives: List<Hive>
        get() = if (searchQuery.isBlank()) hives
        else hives.filter { hive ->
            hive.name.contains(searchQuery, ignoreCase = true) ||
                hive.description?.contains(searchQuery, ignoreCase = true) == true
        }
}

