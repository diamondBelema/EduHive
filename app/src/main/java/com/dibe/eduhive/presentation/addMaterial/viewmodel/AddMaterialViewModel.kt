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
import com.dibe.eduhive.domain.usecase.material.MaterialProcessingProgress

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
            addMaterialUseCase(
                uri = uri,
                hiveId = hiveId,
                title = title,
                hiveContext = ""
            ).collect { progress ->
                when (progress) {
                    is MaterialProcessingProgress.Started -> {
                        _state.update {
                            it.copy(
                                isProcessing = true,
                                processingStatus = "Starting...",
                                progressPercentage = 0
                            )
                        }
                    }

                    is MaterialProcessingProgress.ExtractingText -> {
                        _state.update {
                            it.copy(
                                processingStatus = "Extracting text from file...",
                                progressPercentage = 10
                            )
                        }
                    }

                    is MaterialProcessingProgress.TextExtracted -> {
                        _state.update {
                            it.copy(
                                processingStatus = "Extracted ${progress.characterCount} characters",
                                progressPercentage = 20
                            )
                        }
                    }

                    is MaterialProcessingProgress.MaterialSaved -> {
                        _state.update {
                            it.copy(
                                processingStatus = "Material saved",
                                progressPercentage = 30
                            )
                        }
                    }

                    is MaterialProcessingProgress.ExtractingConcepts -> {
                        _state.update {
                            it.copy(
                                processingStatus = "Analyzing content with AI...",
                                progressPercentage = 40
                            )
                        }
                    }

                    is MaterialProcessingProgress.ConceptsExtracted -> {
                        _state.update {
                            it.copy(
                                processingStatus = "Found ${progress.count} concepts",
                                progressPercentage = 60
                            )
                        }
                    }

                    is MaterialProcessingProgress.GeneratingFlashcards -> {
                        val percentage = 60 + ((progress.current.toFloat() / progress.total) * 30).toInt()
                        _state.update {
                            it.copy(
                                processingStatus = "Generating flashcards ${progress.current}/${progress.total}...",
                                progressPercentage = percentage
                            )
                        }
                    }

                    is MaterialProcessingProgress.Complete -> {
                        _state.update {
                            it.copy(
                                isProcessing = false,
                                processingStatus = null,
                                progressPercentage = 100,
                                successMessage = "✅ Created ${progress.conceptsCreated} concepts " +
                                        "and ${progress.flashcardsCreated} flashcards!",
                                error = null
                            )
                        }
                    }

                    is MaterialProcessingProgress.Failed -> {
                        _state.update {
                            it.copy(
                                isProcessing = false,
                                processingStatus = null,
                                progressPercentage = 0,
                                error = progress.error
                            )
                        }
                    }
                }
            }
        }
    }
}




