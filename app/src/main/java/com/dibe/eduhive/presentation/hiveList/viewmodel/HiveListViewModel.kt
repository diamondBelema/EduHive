package com.dibe.eduhive.presentation.hiveList.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dibe.eduhive.domain.model.Hive
import com.dibe.eduhive.domain.usecase.hive.CreateHiveUseCase
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
    private val selectHiveUseCase: SelectHiveUseCase
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
            is HiveListEvent.ShowCreateDialog -> {
                _state.update { it.copy(showCreateDialog = true) }
            }
            is HiveListEvent.HideCreateDialog -> {
                _state.update { it.copy(showCreateDialog = false) }
            }
            is HiveListEvent.ClearError -> {
                _state.update { it.copy(error = null) }
            }
        }
    }

    private fun loadHives() {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true) }

            getHivesUseCase().fold(
                onSuccess = { hives ->
                    _state.update {
                        it.copy(
                            hives = hives,
                            isLoading = false,
                            error = null
                        )
                    }
                },
                onFailure = { error ->
                    _state.update {
                        it.copy(
                            isLoading = false,
                            error = error.message ?: "Failed to load hives"
                        )
                    }
                }
            )
        }
    }

    private fun createHive(name: String, description: String?) {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true) }

            createHiveUseCase(name, description).fold(
                onSuccess = { hive ->
                    _state.update {
                        it.copy(
                            showCreateDialog = false,
                            isLoading = false
                        )
                    }
                    loadHives()
                },
                onFailure = { error ->
                    _state.update {
                        it.copy(
                            isLoading = false,
                            error = error.message ?: "Failed to create hive"
                        )
                    }
                }
            )
        }
    }

    private fun selectHive(hiveId: String) {
        viewModelScope.launch {
            selectHiveUseCase(hiveId).fold(
                onSuccess = { hive ->
                    _state.update { it.copy(selectedHiveId = hiveId) }
                },
                onFailure = { error ->
                    _state.update {
                        it.copy(error = error.message ?: "Failed to select hive")
                    }
                }
            )
        }
    }
}

