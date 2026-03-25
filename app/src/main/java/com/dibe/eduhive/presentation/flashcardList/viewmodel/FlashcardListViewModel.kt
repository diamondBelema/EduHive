package com.dibe.eduhive.presentation.flashcardList.viewmodel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dibe.eduhive.domain.model.Flashcard
import com.dibe.eduhive.domain.usecase.flashcard.DeleteFlashcardUseCase
import com.dibe.eduhive.domain.usecase.flashcard.GetFlashcardsForConceptUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class FlashcardListViewModel @Inject constructor(
    private val getFlashcardsForConceptUseCase: GetFlashcardsForConceptUseCase,
    private val deleteFlashcardUseCase: DeleteFlashcardUseCase,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val conceptId: String = checkNotNull(savedStateHandle["conceptId"])
    private val conceptName: String = checkNotNull(savedStateHandle["conceptName"])

    private val _state = MutableStateFlow(FlashcardListState())
    val state: StateFlow<FlashcardListState> = _state.asStateFlow()

    init {
        loadFlashcards()
    }

    fun onEvent(event: FlashcardListEvent) {
        when (event) {
            is FlashcardListEvent.DeleteFlashcard -> {
                deleteFlashcard(event.flashcardId)
            }
            is FlashcardListEvent.Reload -> {
                loadFlashcards()
            }
        }
    }

    private fun loadFlashcards() {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true) }

            getFlashcardsForConceptUseCase(conceptId).fold(
                onSuccess = { flashcards ->
                    _state.update {
                        it.copy(
                            flashcards = flashcards,
                            isLoading = false,
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

    private fun deleteFlashcard(flashcardId: String) {
        viewModelScope.launch {
            deleteFlashcardUseCase(flashcardId).fold(
                onSuccess = {
                    _state.update {
                        it.copy(
                            flashcards = it.flashcards.filter { card -> card.id != flashcardId }
                        )
                    }
                },
                onFailure = { error ->
                    _state.update {
                        it.copy(error = error.message ?: "Failed to delete flashcard")
                    }
                }
            )
        }
    }
}

data class FlashcardListState(
    val flashcards: List<Flashcard> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null
)

sealed class FlashcardListEvent {
    data class DeleteFlashcard(val flashcardId: String) : FlashcardListEvent()
    object Reload : FlashcardListEvent()
}

