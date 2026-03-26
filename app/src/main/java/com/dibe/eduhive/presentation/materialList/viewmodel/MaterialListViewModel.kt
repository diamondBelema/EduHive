package com.dibe.eduhive.presentation.materialList.viewmodel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dibe.eduhive.domain.model.Material
import com.dibe.eduhive.domain.usecase.material.GetMaterialsForHiveUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class MaterialListViewModel @Inject constructor(
    private val getMaterialsForHiveUseCase: GetMaterialsForHiveUseCase,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val hiveId: String = checkNotNull(savedStateHandle["hiveId"])

    private val _state = MutableStateFlow(MaterialListState())
    val state: StateFlow<MaterialListState> = _state.asStateFlow()

    init {
        loadMaterials()
    }

    fun loadMaterials() {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true) }
            getMaterialsForHiveUseCase(hiveId).fold(
                onSuccess = { materials ->
                    _state.update { it.copy(isLoading = false, materials = materials) }
                },
                onFailure = { error ->
                    _state.update { it.copy(isLoading = false, error = error.message) }
                }
            )
        }
    }
}

data class MaterialListState(
    val isLoading: Boolean = false,
    val materials: List<Material> = emptyList(),
    val error: String? = null
)
