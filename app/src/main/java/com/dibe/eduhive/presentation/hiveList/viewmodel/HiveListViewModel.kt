package com.dibe.eduhive.presentation.hiveList.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dibe.eduhive.domain.usecase.hive.ArchiveHiveUseCase
import com.dibe.eduhive.domain.usecase.hive.CreateHiveUseCase
import com.dibe.eduhive.domain.usecase.hive.DeleteHiveUseCase
import com.dibe.eduhive.domain.usecase.hive.EditHiveUseCase
import com.dibe.eduhive.domain.usecase.hive.GetHivesUseCase
import com.dibe.eduhive.domain.usecase.hive.SelectHiveUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class HiveListViewModel @Inject constructor(
    private val getHivesUseCase: GetHivesUseCase,
    private val createHiveUseCase: CreateHiveUseCase,
    private val selectHiveUseCase: SelectHiveUseCase,
    private val editHiveUseCase: EditHiveUseCase,
    private val deleteHiveUseCase: DeleteHiveUseCase,
    private val archiveHiveUseCase: ArchiveHiveUseCase
) : ViewModel() {

    private val _state = MutableStateFlow(HiveListState())
    val state: StateFlow<HiveListState> = _state.asStateFlow()

    init {
        loadHives()
    }

    fun onEvent(event: HiveListEvent) {
        when (event) {
            is HiveListEvent.LoadHives -> loadHives()
            is HiveListEvent.CreateHive -> createHive(event.name, event.description)
            is HiveListEvent.SelectHive -> selectHive(event.hiveId)
            is HiveListEvent.ClearSelectedHive -> _state.update { it.copy(selectedHiveId = null) }
            is HiveListEvent.ShowCreateDialog -> _state.update { it.copy(showCreateDialog = true) }
            is HiveListEvent.HideCreateDialog -> _state.update { it.copy(showCreateDialog = false) }
            is HiveListEvent.ShowEditDialog -> _state.update { it.copy(hiveToEdit = event.hive) }
            is HiveListEvent.HideEditDialog -> _state.update { it.copy(hiveToEdit = null) }
            is HiveListEvent.EditHive -> editHive(event.hiveId, event.name, event.description)
            is HiveListEvent.ShowDeleteConfirm -> _state.update { it.copy(hiveToDelete = event.hive) }
            is HiveListEvent.HideDeleteConfirm -> _state.update { it.copy(hiveToDelete = null) }
            is HiveListEvent.DeleteHive -> deleteHive(event.hiveId)
            is HiveListEvent.ArchiveHive -> archiveHive(event.hiveId)
            is HiveListEvent.ClearError -> _state.update { it.copy(error = null) }
        }
    }

    private fun loadHives() {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true) }
            getHivesUseCase().fold(
                onSuccess = { hives ->
                    _state.update { it.copy(hives = hives, isLoading = false, error = null) }
                },
                onFailure = { error ->
                    _state.update { it.copy(isLoading = false, error = error.message ?: "Failed to load hives") }
                }
            )
        }
    }

    private fun createHive(name: String, description: String?) {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true) }
            createHiveUseCase(name, description).fold(
                onSuccess = {
                    _state.update { it.copy(showCreateDialog = false, isLoading = false) }
                    loadHives()
                },
                onFailure = { error ->
                    _state.update { it.copy(isLoading = false, error = error.message ?: "Failed to create hive") }
                }
            )
        }
    }

    private fun selectHive(hiveId: String) {
        viewModelScope.launch {
            selectHiveUseCase(hiveId).fold(
                onSuccess = { _state.update { it.copy(selectedHiveId = hiveId) } },
                onFailure = { error ->
                    _state.update { it.copy(error = error.message ?: "Failed to select hive") }
                }
            )
        }
    }

    private fun editHive(hiveId: String, name: String, description: String?) {
        viewModelScope.launch {
            editHiveUseCase(hiveId, name, description).fold(
                onSuccess = {
                    _state.update { it.copy(hiveToEdit = null) }
                    loadHives()
                },
                onFailure = { error ->
                    _state.update { it.copy(error = error.message ?: "Failed to update hive") }
                }
            )
        }
    }

    private fun deleteHive(hiveId: String) {
        viewModelScope.launch {
            deleteHiveUseCase(hiveId).fold(
                onSuccess = {
                    _state.update { it.copy(hiveToDelete = null) }
                    loadHives()
                },
                onFailure = { error ->
                    _state.update { it.copy(hiveToDelete = null, error = error.message ?: "Failed to delete hive") }
                }
            )
        }
    }

    private fun archiveHive(hiveId: String) {
        viewModelScope.launch {
            archiveHiveUseCase(hiveId).fold(
                onSuccess = { loadHives() },
                onFailure = { error ->
                    _state.update { it.copy(error = error.message ?: "Failed to archive hive") }
                }
            )
        }
    }
}

