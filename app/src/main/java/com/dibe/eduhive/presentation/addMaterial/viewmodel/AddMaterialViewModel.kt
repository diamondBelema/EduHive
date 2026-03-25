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
                                progressPercentage = 0,
                                flashcardsValid = 0,
                                flashcardsRejected = 0,
                                duplicatesFound = 0
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

                    is MaterialProcessingProgress.ExtractingConceptsProgress -> {
                        _state.update {
                            it.copy(
                                processingStatus = "Analyzing content... ${progress.percent}%",
                                progressPercentage = 40 + (progress.percent * 0.2).toInt()
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
                        val percentage = 60 + ((progress.current.toFloat() / progress.total) * 10).toInt()
                        _state.update {
                            it.copy(
                                processingStatus = "Generating flashcards ${progress.current}/${progress.total}...",
                                progressPercentage = percentage
                            )
                        }
                    }

                    is MaterialProcessingProgress.ValidatingFlashcards -> {
                        val percentage = 70 + ((progress.current.toFloat() / progress.total) * 10).toInt()
                        _state.update {
                            it.copy(
                                processingStatus = "Validating flashcards ${progress.current}/${progress.total}...",
                                progressPercentage = percentage
                            )
                        }
                    }

                    is MaterialProcessingProgress.ValidationProgress -> {
                        val percentage = 70 + ((progress.current.toFloat() / progress.total) * 15).toInt()
                        _state.update {
                            it.copy(
                                processingStatus = "Quality check: ✅ ${progress.valid} valid, ❌ ${progress.rejected} rejected",
                                progressPercentage = percentage,
                                flashcardsValid = progress.valid,
                                flashcardsRejected = progress.rejected
                            )
                        }
                    }

                    is MaterialProcessingProgress.RetryingGeneration -> {
                        _state.update {
                            it.copy(
                                processingStatus = "Improving \"${progress.conceptName}\"… (attempt ${progress.attemptNumber})"
                            )
                        }
                    }

                    is MaterialProcessingProgress.DeduplicatingCards -> {
                        _state.update {
                            it.copy(
                                processingStatus = "Checking for duplicates...",
                                progressPercentage = 88
                            )
                        }
                    }

                    is MaterialProcessingProgress.ProcessingSummary -> {
                        _state.update {
                            it.copy(
                                // Keep isProcessing = true so ProcessingStateExpressive stays visible,
                                // showing the quality breakdown. AddMaterialScreen's LaunchedEffect
                                // on successMessage navigates back after a short delay.
                                isProcessing = true,
                                processingStatus = null,
                                progressPercentage = 100,
                                flashcardsValid = progress.flashcardsValid,
                                flashcardsRejected = progress.flashcardsRejected,
                                duplicatesFound = progress.duplicatesFound,
                                successMessage = buildSummaryMessage(progress),
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

    private fun buildSummaryMessage(summary: MaterialProcessingProgress.ProcessingSummary): String {
        val parts = buildList {
            add("✅ ${summary.flashcardsValid} flashcards created")
            if (summary.flashcardsRejected > 0) add("❌ ${summary.flashcardsRejected} rejected")
            if (summary.duplicatesFound > 0) add("⚠️ ${summary.duplicatesFound} duplicates found")
        }
        return parts.joinToString(", ")
    }
}
