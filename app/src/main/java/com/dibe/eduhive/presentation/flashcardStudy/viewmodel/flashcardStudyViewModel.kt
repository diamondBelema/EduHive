package com.dibe.eduhive.presentation.flashcardStudy.viewmodel


import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dibe.eduhive.domain.model.enums.ConfidenceLevel
import com.dibe.eduhive.domain.usecase.review.ReviewFlashcardUseCase
import com.dibe.eduhive.domain.usecase.study.GetNextReviewItemsUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class FlashcardStudyViewModel @Inject constructor(
    private val getNextReviewItemsUseCase: GetNextReviewItemsUseCase,
    private val reviewFlashcardUseCase: ReviewFlashcardUseCase,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val hiveId: String = checkNotNull(savedStateHandle["hiveId"])

    private val _state = MutableStateFlow(FlashcardStudyState())
    val state: StateFlow<FlashcardStudyState> = _state.asStateFlow()

    init {
        loadNextFlashcards()
    }

    fun onEvent(event: FlashcardStudyEvent) {
        when (event) {
            is FlashcardStudyEvent.FlipCard -> {
                _state.update { it.copy(isFlipped = !it.isFlipped) }
            }
            is FlashcardStudyEvent.RateConfidence -> {
                rateFlashcard(event.level)
            }
            is FlashcardStudyEvent.Reload -> {
                loadNextFlashcards()
            }
        }
    }

    private fun loadNextFlashcards() {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true) }

            getNextReviewItemsUseCase(limit = 20).fold(
                onSuccess = { flashcards ->
                    _state.update {
                        it.copy(
                            flashcards = flashcards,
                            currentIndex = 0,
                            isLoading = false,
                            isFlipped = false,
                            error = null
                        )
                    }
                },
                onFailure = { error ->
                    _state.update {
                        it.copy(
                            isLoading = false,
                            error = error.message ?: "Failed to load flashcards"
                        )
                    }
                }
            )
        }
    }

    private fun rateFlashcard(level: ConfidenceLevel) {
        val currentFlashcard = state.value.flashcards.getOrNull(state.value.currentIndex)
            ?: return

        viewModelScope.launch {
            reviewFlashcardUseCase(
                flashcardId = currentFlashcard.id,
                confidenceLevel = level
            ).fold(
                onSuccess = {
                    // Move to next flashcard
                    val nextIndex = state.value.currentIndex + 1

                    if (nextIndex >= state.value.flashcards.size) {
                        // All done!
                        _state.update {
                            it.copy(
                                isComplete = true,
                                completedCount = state.value.flashcards.size
                            )
                        }
                    } else {
                        _state.update {
                            it.copy(
                                currentIndex = nextIndex,
                                isFlipped = false
                            )
                        }
                    }
                },
                onFailure = { error ->
                    _state.update {
                        it.copy(error = error.message ?: "Failed to review flashcard")
                    }
                }
            )
        }
    }
}


