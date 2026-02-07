package com.dibe.eduhive.presentation.addMaterial.viewmodel

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dibe.eduhive.domain.usecase.material.AddMaterialUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AddMaterialViewModel @Inject constructor(
    private val addMaterialUseCase: AddMaterialUseCase,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val hiveId: String = checkNotNull(savedStateHandle["hiveId"])

    private val _state = MutableStateFlow(AddMaterialState())
    val state: StateFlow<AddMaterialState> = _state.asStateFlow()

    fun onEvent(event: AddMaterialEvent) {
        when (event) {
            is AddMaterialEvent.SelectFile -> handleFileSelected(event.uri, event.title)
            is AddMaterialEvent.DismissSuccess -> {
                _state.update { it.copy(successMessage = null) }
            }
            is AddMaterialEvent.ClearError -> {
                _state.update { it.copy(error = null) }
            }
        }
    }

    private fun handleFileSelected(uri: Uri, title: String) {
        viewModelScope.launch {
            _state.update {
                it.copy(
                    isProcessing = true,
                    processingStatus = "Extracting text from file..."
                )
            }

            addMaterialUseCase(
                uri = uri,
                hiveId = hiveId,
                title = title,
                hiveContext = "" // Can get from hive later
            ).fold(
                onSuccess = { result ->
                    _state.update {
                        it.copy(
                            isProcessing = false,
                            processingStatus = null,
                            successMessage = "✅ Created ${result.conceptsCreated} concepts " +
                                    "and ${result.flashcardsCreated} flashcards!",
                            error = null
                        )
                    }
                },
                onFailure = { error ->
                    _state.update {
                        it.copy(
                            isProcessing = false,
                            processingStatus = null,
                            error = error.message ?: "Failed to process material"
                        )
                    }
                }
            )
        }
    }
}



