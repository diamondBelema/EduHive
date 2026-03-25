package com.dibe.eduhive.presentation.conceptList.viewmodel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dibe.eduhive.domain.model.Concept
import com.dibe.eduhive.domain.usecase.concept.GetConceptsByHiveUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ConceptListViewModel @Inject constructor(
    private val getConceptsByHiveUseCase: GetConceptsByHiveUseCase,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val hiveId: String = checkNotNull(savedStateHandle["hiveId"])

    private val _state = MutableStateFlow(ConceptListState())
    val state: StateFlow<ConceptListState> = _state.asStateFlow()

    init {
        loadConcepts()
    }

    fun loadConcepts() {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true) }
            getConceptsByHiveUseCase(hiveId).fold(
                onSuccess = { concepts ->
                    _state.update { it.copy(isLoading = false, concepts = concepts) }
                },
                onFailure = { error ->
                    _state.update { it.copy(isLoading = false, error = error.message) }
                }
            )
        }
    }
}

data class ConceptListState(
    val isLoading: Boolean = false,
    val concepts: List<Concept> = emptyList(),
    val error: String? = null
)
