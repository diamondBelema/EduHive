package com.dibe.eduhive.presentation.flashcardStudy.viewmodel


import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dibe.eduhive.domain.model.enums.ConfidenceLevel
import com.dibe.eduhive.domain.repository.FlashcardRepository
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
    private val flashcardRepository: FlashcardRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val hiveId: String = checkNotNull(savedStateHandle["hiveId"])
    private val isAllHives: Boolean get() = hiveId == "ALL"

    private val _state = MutableStateFlow(FlashcardStudyState())
    val state: StateFlow<FlashcardStudyState> = _state.asStateFlow()

    init {
        loadScheduledFlashcards()
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
                loadScheduledFlashcards()
            }
            is FlashcardStudyEvent.StudyAnyway -> {
                startFreePractice()
            }
        }
    }

    /** Load cards according to the SRS schedule (normal study mode). */
    private fun loadScheduledFlashcards() {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, isFreePractice = false) }

            getNextReviewItemsUseCase(
                hiveId = if (isAllHives) null else hiveId,
                limit = 20,
                allowContinueWhenNoDue = false
            ).fold(
                onSuccess = { flashcards ->
                    _state.update {
                        it.copy(
                            flashcards = flashcards,
                            currentIndex = 0,
                            isLoading = false,
                            isFlipped = false,
                            isComplete = false,
                            completedCount = 0,
                            isFreePractice = false,
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

    /**
     * Free-practice mode: loads ALL flashcards for the hive (or all hives) without scheduling
     * constraint. Ratings in this mode only move through the deck — they do NOT update
     * Leitner boxes or Bayesian confidence, preserving the algorithm's state.
     */
    private fun startFreePractice() {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true) }
            try {
                val allCards = if (isAllHives) {
                    flashcardRepository.getAllFlashcardsForStudy(limit = 100)
                } else {
                    flashcardRepository.getStudyFallbackFlashcards(
                        hiveId = hiveId,
                        limit = 100
                    )
                }
                _state.update {
                    it.copy(
                        flashcards = allCards,
                        currentIndex = 0,
                        isLoading = false,
                        isFlipped = false,
                        isComplete = false,
                        completedCount = 0,
                        isFreePractice = true,
                        error = if (allCards.isEmpty()) "No flashcards in this hive yet." else null
                    )
                }
            } catch (e: Exception) {
                _state.update {
                    it.copy(
                        isLoading = false,
                        error = e.message ?: "Could not load flashcards"
                    )
                }
            }
        }
    }

    private fun rateFlashcard(level: ConfidenceLevel) {
        val currentFlashcard = state.value.flashcards.getOrNull(state.value.currentIndex)
            ?: return

        viewModelScope.launch {
            if (state.value.isFreePractice) {
                // Free-practice: skip SRS update, just advance to next card
                advanceToNextCard()
            } else {
                reviewFlashcardUseCase(
                    flashcardId = currentFlashcard.id,
                    confidenceLevel = level
                ).fold(
                    onSuccess = { advanceToNextCard() },
                    onFailure = { error ->
                        _state.update {
                            it.copy(error = error.message ?: "Failed to review flashcard")
                        }
                    }
                )
            }
        }
    }

    private fun advanceToNextCard() {
        val nextIndex = state.value.currentIndex + 1
        if (nextIndex >= state.value.flashcards.size) {
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
    }
}