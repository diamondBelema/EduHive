package com.dibe.eduhive.presentation.hiveList.viewmodel

import com.dibe.eduhive.domain.model.Hive


// State
data class HiveListState(
    val hives: List<Hive> = emptyList(),
    val isLoading: Boolean = false,
    val showCreateDialog: Boolean = false,
    val selectedHiveId: String? = null,
    val error: String? = null
)

